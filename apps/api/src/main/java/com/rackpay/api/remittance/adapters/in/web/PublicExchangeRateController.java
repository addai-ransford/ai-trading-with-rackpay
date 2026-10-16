package com.rackpay.api.remittance.adapters.in.web;

import com.rackpay.api.remittance.adapters.out.providers.fx.ExchangeRateApiClient;
import com.rackpay.api.shared.core.money.Currency;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/public/exchange-rates")
public class PublicExchangeRateController {
    private static final MathContext RATE_PRECISION = new MathContext(12);
    private final ExchangeRateApiClient exchangeRates;

    public PublicExchangeRateController(ExchangeRateApiClient exchangeRates) {
        this.exchangeRates = exchangeRates;
    }

    @GetMapping
    public List<ExchangeRateResponse> list() {
        Map<Currency, BigDecimal> eurRates = exchangeRates.getEurRates();
        List<ExchangeRateResponse> availableRates = new ArrayList<>();

        for (Currency source : Currency.values()) {
            BigDecimal sourcePerEur = eurRates.get(source);
            if (sourcePerEur == null || sourcePerEur.signum() <= 0) {
                continue;
            }

            for (Currency destination : Currency.values()) {
                if (source == destination) {
                    continue;
                }

                BigDecimal destinationPerEur = eurRates.get(destination);
                if (destinationPerEur == null || destinationPerEur.signum() <= 0) {
                    continue;
                }

                // ExchangeRate-API returns units of each currency per EUR.
                // Divide to derive the indicative source-to-destination rate.
                BigDecimal rate = destinationPerEur.divide(sourcePerEur, RATE_PRECISION);
                availableRates.add(new ExchangeRateResponse(
                    source.name(), destination.name(), rate, "INDICATIVE"
                ));
            }
        }

        return availableRates.stream()
            .sorted(Comparator.comparing((ExchangeRateResponse rate) -> !"EUR".equals(rate.sourceCurrency()))
                .thenComparing(ExchangeRateResponse::sourceCurrency)
                .thenComparing(ExchangeRateResponse::destinationCurrency))
            .toList();
    }

    public record ExchangeRateResponse(
        String sourceCurrency,
        String destinationCurrency,
        BigDecimal rate,
        String rateType
    ) {}
}
