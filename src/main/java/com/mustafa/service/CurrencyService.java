package com.mustafa.service;

import com.mustafa.dto.response.ExchangeRateResponse;

public interface CurrencyService {
    // Belirli bir para birimine (Örn: TRY) göre canlı kurları getirir
    ExchangeRateResponse getLiveRates(String baseCurrency);

    // Transfer işlemi için iki para birimi arasındaki çevirim tutarını hesaplar
    Double convertAmount(Double amount, String fromCurrency, String toCurrency);
}