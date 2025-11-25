package com.nirvana.application.service;

public interface CurrencyConversionService {

    ConversionResult convert(int amountCents, String fromCurrency, String toCurrency);

    record ConversionResult(int convertedCents, double rateUsed, String sourceCurrency, String targetCurrency) {
    }
}
