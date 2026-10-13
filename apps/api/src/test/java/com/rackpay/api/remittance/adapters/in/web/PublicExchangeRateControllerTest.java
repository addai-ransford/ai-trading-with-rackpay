package com.rackpay.api.remittance.adapters.in.web;

import com.rackpay.api.remittance.adapters.out.providers.fx.ExchangeRateApiClient;
import com.rackpay.api.shared.core.money.Currency;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PublicExchangeRateControllerTest {

    @Test
    void listsSupportedCurrencyPairsFromEurBasedProviderRates() {
        ExchangeRateApiClient exchangeRates = mock(ExchangeRateApiClient.class);
        when(exchangeRates.getEurRates()).thenReturn(Map.of(
            Currency.EUR, BigDecimal.ONE,
            Currency.GHS, new BigDecimal("12.3456")
        ));
        PublicExchangeRateController controller = new PublicExchangeRateController(exchangeRates);

        List<PublicExchangeRateController.ExchangeRateResponse> rates = controller.list();

        assertEquals(2, rates.size());
        assertEquals("EUR", rates.get(0).sourceCurrency());
        assertEquals("GHS", rates.get(0).destinationCurrency());
        assertEquals(new BigDecimal("12.3456"), rates.get(0).rate());
        assertEquals("INDICATIVE", rates.get(0).rateType());

        assertEquals("GHS", rates.get(1).sourceCurrency());
        assertEquals("EUR", rates.get(1).destinationCurrency());
        assertEquals(new BigDecimal("0.081000518403"), rates.get(1).rate());
    }

    @Test
    void returnsNoRatesWhenProviderHasNoSupportedCurrencies() {
        ExchangeRateApiClient exchangeRates = mock(ExchangeRateApiClient.class);
        when(exchangeRates.getEurRates()).thenReturn(Map.of());
        PublicExchangeRateController controller = new PublicExchangeRateController(exchangeRates);

        assertEquals(List.of(), controller.list());
    }
}
