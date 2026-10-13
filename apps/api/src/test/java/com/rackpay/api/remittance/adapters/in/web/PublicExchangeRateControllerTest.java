package com.rackpay.api.remittance.adapters.in.web;

import com.rackpay.api.remittance.ports.out.FxRateProvider;
import com.rackpay.api.shared.core.money.Currency;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PublicExchangeRateControllerTest {

    @Test
    void listsOnlyRatesAvailableFromTheConfiguredProvider() {
        FxRateProvider provider = mock(FxRateProvider.class);
        when(provider.getRate(Currency.EUR, Currency.GHS)).thenReturn(new BigDecimal("12.3456"));
        PublicExchangeRateController controller = new PublicExchangeRateController(provider);

        List<PublicExchangeRateController.ExchangeRateResponse> rates = controller.list();

        assertEquals(1, rates.size());
        assertEquals("EUR", rates.get(0).sourceCurrency());
        assertEquals("GHS", rates.get(0).destinationCurrency());
        assertEquals(new BigDecimal("12.3456"), rates.get(0).rate());
        assertEquals("INDICATIVE", rates.get(0).rateType());
    }
}
