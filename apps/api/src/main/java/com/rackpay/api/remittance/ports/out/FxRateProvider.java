package com.rackpay.api.remittance.ports.out;
import com.rackpay.api.shared.core.money.Currency;
import java.math.BigDecimal;
public interface FxRateProvider { BigDecimal getRate(Currency sourceCurrency,Currency destinationCurrency); }
