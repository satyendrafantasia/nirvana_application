package com.nirvana.application.service.impl;

import com.nirvana.application.config.CurrencyConversionProperties;
import com.nirvana.application.service.CurrencyConversionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class CurrencyConversionServiceImpl implements CurrencyConversionService {

    private final CurrencyConversionProperties properties;

    @Override
    public ConversionResult convert(int amountCents, String fromCurrency, String toCurrency) {
        String source = normalize(fromCurrency, properties.getBase());
        String target = normalize(toCurrency, properties.getBase());

        if (source.equals(target)) {
            return new ConversionResult(amountCents, 1.0d, source, target);
        }

        Map<String, Double> rates = properties.getRates();
        double sourceRate = rates.getOrDefault(source, 1.0d);
        double targetRate = rates.getOrDefault(target, 1.0d);
        if (sourceRate <= 0 || targetRate <= 0) {
            log.warn("Invalid conversion rates. Falling back to 1:1 for {} -> {}", source, target);
            return new ConversionResult(amountCents, 1.0d, source, target);
        }

        double rate = targetRate / sourceRate;
        int converted = BigDecimal.valueOf(amountCents)
                .multiply(BigDecimal.valueOf(rate))
                .setScale(0, RoundingMode.HALF_UP)
                .intValue();
        return new ConversionResult(converted, rate, source, target);
    }

    private String normalize(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback.toUpperCase(Locale.ROOT);
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }
}
