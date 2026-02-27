package com.mustafa.controller.impl;

import com.mustafa.controller.CurrencyController;
import com.mustafa.dto.response.ExchangeRateResponse;
import com.mustafa.service.CurrencyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/currencies")
@RequiredArgsConstructor
public class CurrencyControllerImpl implements CurrencyController {

    private final CurrencyService currencyService;

    @Override
    @GetMapping("/rates")
    public ResponseEntity<ExchangeRateResponse> getRates(String base) {
        return ResponseEntity.ok(currencyService.getLiveRates(base));
    }
}