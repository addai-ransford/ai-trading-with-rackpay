package com.rackpay.api.remittance.adapters.out.persistence;

import com.rackpay.api.payment.adapters.out.providers.mollie.Amount;
import com.rackpay.api.shared.core.money.Currency;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(name="remittance_corridors")
public class RemittanceCorridorEntity {
 @Id private UUID id;
 @Column(name="source_country_code",nullable=false,length=2) private String sourceCountryCode;
 @Column(name="destination_country_code",nullable=false,length=2) private String destinationCountryCode;
 @Column(name="source_currency_code",nullable=false,length=3) private String sourceCurrencyCode;
 @Column(name="destination_currency_code",nullable=false,length=3) private String destinationCurrencyCode;
 @Column(nullable=false) private boolean enabled;
 @Column(name="min_amount",nullable=false,precision=38,scale=18) private BigDecimal minAmount;
 @Column(name="max_amount",precision=38,scale=18) private BigDecimal maxAmount;
 @Column(name="fee_fixed_amount",nullable=false,precision=38,scale=18) private BigDecimal feeFixedAmount;
 @Column(name="fee_currency_code",nullable=false,length=3) private String feeCurrencyCode;
 @Column(name="fee_bps",nullable=false) private int feeBps;
 @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt;
 @Column(name="updated_at",nullable=false) private Instant updatedAt;
 protected RemittanceCorridorEntity(){}
 public UUID getId(){return id;} public String getSourceCountryCode(){return sourceCountryCode;}
 public String getDestinationCountryCode(){return destinationCountryCode;} public String getSourceCurrencyCode(){return sourceCurrencyCode;}
 public String getDestinationCurrencyCode(){return destinationCurrencyCode;} public boolean isEnabled(){return enabled;}
 public BigDecimal getMinAmount(){return minAmount;} public BigDecimal getMaxAmount(){return maxAmount;}
 public BigDecimal getFeeFixedAmount(){return feeFixedAmount;} public String getFeeCurrencyCode(){return feeCurrencyCode;}
 public int getFeeBps(){return feeBps;}
}
