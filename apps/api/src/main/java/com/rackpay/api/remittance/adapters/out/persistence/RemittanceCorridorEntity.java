package com.rackpay.api.remittance.adapters.out.persistence;

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
 @Column(name="fx_rate",precision=38,scale=18) private BigDecimal fxRate;
 @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt;
 @Column(name="updated_at",nullable=false) private Instant updatedAt;
 protected RemittanceCorridorEntity(){}
 public RemittanceCorridorEntity(UUID id, String sourceCountryCode, String destinationCountryCode,
   String sourceCurrencyCode, String destinationCurrencyCode, boolean enabled, BigDecimal minAmount,
   BigDecimal maxAmount, BigDecimal feeFixedAmount, String feeCurrencyCode, int feeBps) {
  this.id=id; this.sourceCountryCode=sourceCountryCode; this.destinationCountryCode=destinationCountryCode;
  this.sourceCurrencyCode=sourceCurrencyCode; this.destinationCurrencyCode=destinationCurrencyCode;
  this.enabled=enabled; this.minAmount=minAmount; this.maxAmount=maxAmount; this.feeFixedAmount=feeFixedAmount;
  this.feeCurrencyCode=feeCurrencyCode; this.feeBps=feeBps; this.createdAt=Instant.now(); this.updatedAt=this.createdAt;
 }
 public void update(boolean enabled, BigDecimal minAmount, BigDecimal maxAmount, BigDecimal feeFixedAmount, int feeBps, BigDecimal fxRate) {
  this.enabled=enabled; this.minAmount=minAmount; this.maxAmount=maxAmount; this.feeFixedAmount=feeFixedAmount;
  this.feeBps=feeBps; this.fxRate=fxRate; this.updatedAt=Instant.now();
 }
 public UUID getId(){return id;} public String getSourceCountryCode(){return sourceCountryCode;}
 public String getDestinationCountryCode(){return destinationCountryCode;} public String getSourceCurrencyCode(){return sourceCurrencyCode;}
 public String getDestinationCurrencyCode(){return destinationCurrencyCode;} public boolean isEnabled(){return enabled;}
 public BigDecimal getMinAmount(){return minAmount;} public BigDecimal getMaxAmount(){return maxAmount;}
 public BigDecimal getFeeFixedAmount(){return feeFixedAmount;} public String getFeeCurrencyCode(){return feeCurrencyCode;}
 public int getFeeBps(){return feeBps;}
 public BigDecimal getFxRate(){return fxRate;}
}
