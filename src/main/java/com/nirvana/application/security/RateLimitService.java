package com.nirvana.application.security;

import com.nirvana.application.config.RateLimitingProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
@Slf4j
public class RateLimitService {

    private final RateLimitingProperties properties;
    private final StringRedisTemplate redisTemplate;
    private final MeterRegistry meterRegistry;

    private final Map<String, Window> fallbackWindows = new ConcurrentHashMap<>();

    private final Counter hitCounter;
    private final Counter blockCounter;

    public RateLimitService(RateLimitingProperties properties, StringRedisTemplate redisTemplate,
                            MeterRegistry meterRegistry) {
        this.properties = properties;
        this.redisTemplate = redisTemplate;
        this.meterRegistry = meterRegistry;
        this.hitCounter = meterRegistry.counter("rate_limit_requests");
        this.blockCounter = meterRegistry.counter("rate_limit_blocks");
    }

    public boolean isAllowed(String key) {
        return isAllowed(key, properties.getRequests());
    }

    public boolean isAllowed(String key, int maxRequests) {
        hitCounter.increment();
        try {
            Long value = redisTemplate.opsForValue().increment(key);
            if (value != null && value == 1) {
                redisTemplate.expire(key, Duration.ofSeconds(properties.getWindowSeconds()));
            }
            if (value != null && value > maxRequests) {
                blockCounter.increment();
                return false;
            }
            return true;
        } catch (Exception ex) {
            // fall back to JVM memory to avoid failing open
            log.debug("Redis unavailable for rate limiting, falling back to in-memory", ex);
            return fallbackAllowed(key, maxRequests);
        }
    }

    private boolean fallbackAllowed(String key, int maxRequests) {
        Instant now = Instant.now();
        Window window = fallbackWindows.computeIfAbsent(key, k -> new Window());
        synchronized (window) {
            window.evictOld(now, properties.getWindowSeconds());
            if (window.requests >= maxRequests) {
                blockCounter.increment();
                return false;
            }
            window.requests++;
            window.lastRequests[window.requests % window.lastRequests.length] = now;
            return true;
        }
    }

    private static class Window {
        private int requests = 0;
        private final Instant[] lastRequests = new Instant[256];

        void evictOld(Instant now, int windowSeconds) {
            Instant threshold = now.minusSeconds(windowSeconds);
            for (int i = 0; i < lastRequests.length; i++) {
                Instant ts = lastRequests[i];
                if (ts != null && ts.isBefore(threshold)) {
                    requests--;
                    lastRequests[i] = null;
                }
            }
            if (requests < 0) {
                requests = 0;
            }
        }
    }
}
