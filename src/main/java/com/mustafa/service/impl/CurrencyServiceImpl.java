package com.mustafa.service.impl;

import com.mustafa.dto.response.ExchangeRateResponse;
import com.mustafa.exception.BankOperationException;
import com.mustafa.service.CurrencyService;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class CurrencyServiceImpl implements CurrencyService {

    // Spring'in dış dünyayla konuşmasını sağlayan HTTP aracı
    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public ExchangeRateResponse getLiveRates(String baseCurrency) {
        // Ücretsiz ve API Key istemeyen kur sağlayıcısı
        String url = "https://open.er-api.com/v6/latest/" + baseCurrency.toUpperCase();

        try {
            // Dış API'ye GET isteği at ve gelen JSON'ı bizim DTO'ya dönüştür
            return restTemplate.getForObject(url, ExchangeRateResponse.class);
        } catch (Exception e) {
            throw new BankOperationException("Canlı kurlar çekilirken bir hata oluştu: " + e.getMessage());
        }
    }

    @Override
    public Double convertAmount(Double amount, String fromCurrency, String toCurrency) {
        if (fromCurrency.equalsIgnoreCase(toCurrency)) {
            return amount; // Aynı birimse çevirme yapma
        }

        // 1. "From" para birimine göre tüm kurları çek (Örn: TRY)
        ExchangeRateResponse response = getLiveRates(fromCurrency);

        // 2. Hedef para biriminin (Örn: USD) karşılığını al
        Double rate = response.getRates().get(toCurrency.toUpperCase());

        if (rate == null) {
            throw new BankOperationException("Desteklenmeyen para birimi: " + toCurrency);
        }

        // 3. Miktarı kurla çarp ve dön (Örn: 1000 TRY * 0.030 USD)
        return amount * rate;
    }
}