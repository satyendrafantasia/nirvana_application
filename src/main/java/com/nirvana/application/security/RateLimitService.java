package com.nirvana.application.security;

import com.nirvana.application.config.RateLimitingProperties;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
@RequiredArgsConstructor
public class RateLimitService {
    private final RateLimitingProperties properties;
    private final StringRedisTemplate redisTemplate;
    private final MeterRegistry metrics;
    private final SharedRateWindow shared;
    private static final DefaultRedisScript<Long> INCREMENT = new DefaultRedisScript<>("""
            local n = redis.call('INCR', KEYS[1])
            if n == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end
            return n
            """, Long.class);

    public boolean isAllowed(String key, int limit) {
        try {
            Long count = redisTemplate.execute(INCREMENT, List.of("nirvana:rate:" + key),
                    String.valueOf(properties.getWindowSeconds()));
            if (count != null) return record(count <= limit);
        } catch (DataAccessException unavailable) {
            metrics.counter("rate_limit_redis_unavailable").increment();
        }
        return isAllowedSensitive(key, limit);
    }

    /** Sensitive operations always use the same durable primary, even during Redis failures. */
    public boolean isAllowedSensitive(String key, int limit) {
        return record(shared.consume(key, limit, properties.getWindowSeconds()));
    }

    private boolean record(boolean allowed) {
        metrics.counter("rate_limit_requests").increment();
        if (!allowed) metrics.counter("rate_limit_blocks").increment();
        return allowed;
    }
}
