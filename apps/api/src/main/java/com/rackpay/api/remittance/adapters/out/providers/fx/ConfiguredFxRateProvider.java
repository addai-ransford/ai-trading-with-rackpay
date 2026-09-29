package com.rackpay.api.remittance.adapters.out.providers.fx;

import com.rackpay.api.remittance.ports.out.FxRateProvider;
import com.rackpay.api.shared.core.money.Currency;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.util.Map;
@Component
public class ConfiguredFxRateProvider implements FxRateProvider {
 private final Map<String,BigDecimal> rates;
 public ConfiguredFxRateProvider(@Value("#{${rackpay.remittance.fx-rates:{}}}") Map<String,BigDecimal> rates){this.rates=rates;}
 public BigDecimal getRate(Currency sourceCurrency,Currency destinationCurrency){
  if(sourceCurrency==destinationCurrency)return BigDecimal.ONE;
  BigDecimal rate=rates.get(sourceCurrency.name()+"_"+destinationCurrency.name());
  if(rate==null||rate.signum()<=0)throw new IllegalStateException("No FX rate is configured for "+sourceCurrency+"/"+destinationCurrency);
  return rate;
 }
}
