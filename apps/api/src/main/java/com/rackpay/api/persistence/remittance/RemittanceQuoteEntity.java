package com.rackpay.api.persistence.remittance;
import com.rackpay.api.domain.money.Currency;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(name="remittance_quotes")
public class RemittanceQuoteEntity {
 @Id private UUID id;
 @Column(name="user_id",nullable=false) private UUID userId;
 @Column(name="corridor_id",nullable=false) private UUID corridorId;
 @Column(name="recipient_id",nullable=false) private UUID recipientId;
 @Column(name="source_amount",nullable=false,precision=38,scale=18) private BigDecimal sourceAmount;
 @Enumerated(EnumType.STRING) @Column(name="source_currency_code",nullable=false,length=3) private Currency sourceCurrency;
 @Column(name="destination_amount",nullable=false,precision=38,scale=18) private BigDecimal destinationAmount;
 @Enumerated(EnumType.STRING) @Column(name="destination_currency_code",nullable=false,length=3) private Currency destinationCurrency;
 @Column(name="fee_amount",nullable=false,precision=38,scale=18) private BigDecimal feeAmount;
 @Enumerated(EnumType.STRING) @Column(name="fee_currency_code",nullable=false,length=3) private Currency feeCurrency;
 @Column(name="fx_rate",nullable=false,precision=38,scale=18) private BigDecimal fxRate;
 @Column(name="expires_at",nullable=false) private Instant expiresAt;
 @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt;
 protected RemittanceQuoteEntity(){}
 public RemittanceQuoteEntity(UUID id,UUID userId,UUID corridorId,UUID recipientId,BigDecimal sourceAmount,Currency sourceCurrency,BigDecimal destinationAmount,Currency destinationCurrency,BigDecimal feeAmount,Currency feeCurrency,BigDecimal fxRate,Instant expiresAt,Instant createdAt){
  this.id=id;this.userId=userId;this.corridorId=corridorId;this.recipientId=recipientId;this.sourceAmount=sourceAmount;this.sourceCurrency=sourceCurrency;this.destinationAmount=destinationAmount;this.destinationCurrency=destinationCurrency;this.feeAmount=feeAmount;this.feeCurrency=feeCurrency;this.fxRate=fxRate;this.expiresAt=expiresAt;this.createdAt=createdAt;
 }
 public UUID getId(){return id;} public UUID getUserId(){return userId;} public UUID getCorridorId(){return corridorId;} public UUID getRecipientId(){return recipientId;}
 public BigDecimal getSourceAmount(){return sourceAmount;} public Currency getSourceCurrency(){return sourceCurrency;} public BigDecimal getDestinationAmount(){return destinationAmount;} public Currency getDestinationCurrency(){return destinationCurrency;}
 public BigDecimal getFeeAmount(){return feeAmount;} public Currency getFeeCurrency(){return feeCurrency;} public BigDecimal getFxRate(){return fxRate;} public Instant getExpiresAt(){return expiresAt;} public Instant getCreatedAt(){return createdAt;}
}
