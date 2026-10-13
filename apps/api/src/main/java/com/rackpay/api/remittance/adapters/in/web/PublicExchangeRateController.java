package com.rackpay.api.remittance.adapters.in.web;

import com.rackpay.api.remittance.ports.out.FxRateProvider;
import com.rackpay.api.shared.core.money.Currency;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@RestController
@RequestMapping("/api/v1/public/exchange-rates")
public class PublicExchangeRateController {
    private final FxRateProvider fxRates;

    public PublicExchangeRateController(FxRateProvider fxRates) {
        this.fxRates = fxRates;
    }

    @GetMapping
    public List<ExchangeRateResponse> list() {
        List<ExchangeRateResponse> availableRates = new ArrayList<>();

        for (Currency source : Currency.values()) {
            for (Currency destination : Currency.values()) {
                if (source == destination) {
                    continue;
                }

                try {
                    BigDecimal rate = fxRates.getRate(source, destination);
                    if (rate != null && rate.signum() > 0) {
                        availableRates.add(new ExchangeRateResponse(
                            source.name(), destination.name(), rate, "INDICATIVE"
                        ));
                    }
                } catch (IllegalStateException unavailable) {
                    // An unconfigured corridor is omitted rather than publishing a guessed rate.
                }
            }
        }

        return availableRates.stream()
            .sorted(Comparator.comparing(ExchangeRateResponse::sourceCurrency)
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
