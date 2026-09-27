package com.rackpay.api.remittance;
import com.rackpay.api.domain.money.Currency;
import java.math.BigDecimal;
public interface FxRateProvider { BigDecimal getRate(Currency sourceCurrency,Currency destinationCurrency); }
