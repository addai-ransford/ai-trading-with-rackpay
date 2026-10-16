package com.rackpay.api.remittance.adapters.out.providers.fx;

import com.rackpay.api.shared.core.money.Currency;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * Fetches EUR-based indicative rates for the public rates page.
 *
 * This client is deliberately separate from ConfiguredFxRateProvider: public
 * market indications must never silently become execution rates for remittances.
 */
@Component
public class ExchangeRateApiClient {
    private static final Logger log = LoggerFactory.getLogger(ExchangeRateApiClient.class);
    private static final Duration CACHE_TTL = Duration.ofHours(1);

    private final RestClient restClient;
    private final String apiKey;
    private volatile CachedRates cachedRates = new CachedRates(Map.of(), Instant.EPOCH);

    public ExchangeRateApiClient(
        RestClient.Builder restClientBuilder,
        @Value("${rackpay.public-fx.api-key:}") String apiKey
    ) {
        this.restClient = restClientBuilder.build();
        this.apiKey = apiKey == null ? "" : apiKey.trim();
    }

    public Map<Currency, BigDecimal> getEurRates() {
        CachedRates current = cachedRates;
        if (!current.rates().isEmpty() && Instant.now().isBefore(current.fetchedAt().plus(CACHE_TTL))) {
            return current.rates();
        }

        if (apiKey.isBlank()) {
            return current.rates();
        }

        try {
            ExchangeRateResponse response = restClient.get()
                .uri("https://v6.exchangerate-api.com/v6/{apiKey}/latest/EUR", apiKey)
                .retrieve()
                .body(ExchangeRateResponse.class);

            if (response == null || !"success".equalsIgnoreCase(response.result())
                || response.conversionRates() == null) {
                log.warn("ExchangeRate-API returned an invalid response for the public rates feed");
                return current.rates();
            }

            Map<Currency, BigDecimal> supportedRates = new EnumMap<>(Currency.class);
            supportedRates.put(Currency.EUR, BigDecimal.ONE);
            response.conversionRates().forEach((code, rate) -> {
                try {
                    Currency currency = Currency.valueOf(code);
                    if (rate != null && rate.signum() > 0) {
                        supportedRates.put(currency, rate);
                    }
                } catch (IllegalArgumentException ignored) {
                    // Ignore currencies that RackPay does not support.
                }
            });

            Map<Currency, BigDecimal> immutableRates = Collections.unmodifiableMap(supportedRates);
            cachedRates = new CachedRates(immutableRates, Instant.now());
            return immutableRates;
        } catch (RestClientException exception) {
            log.warn("Unable to refresh public FX rates from ExchangeRate-API: {}", exception.getMessage());
            return current.rates();
        }
    }

    private record CachedRates(Map<Currency, BigDecimal> rates, Instant fetchedAt) {}

    private record ExchangeRateResponse(
        String result,
        @com.fasterxml.jackson.annotation.JsonProperty("conversion_rates")
        Map<String, BigDecimal> conversionRates
    ) {}
}
