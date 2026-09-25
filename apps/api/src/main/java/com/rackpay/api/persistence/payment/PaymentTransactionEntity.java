package com.rackpay.api.persistence.payment;

import com.rackpay.api.domain.money.Currency;
import com.rackpay.api.payment.PaymentProviderType;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="payment_transactions",uniqueConstraints=@UniqueConstraint(name="uq_payment_transaction_provider_payment",columnNames={"provider","provider_payment_id"}))
public class PaymentTransactionEntity {
    @Id private UUID id;
    @Column(name="financial_transaction_id") private UUID financialTransactionId;
    @Column(name="user_id",nullable=false) private UUID userId;
    @Column(name="wallet_id",nullable=false) private UUID walletId;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private PaymentProviderType provider;
    @Column(name="provider_payment_id",length=255) private String providerPaymentId;
    @Column(nullable=false,length=30) private String status;
    @Column(nullable=false,precision=38,scale=18) private BigDecimal amount;
    @Enumerated(EnumType.STRING) @Column(name="currency_code",nullable=false,length=3) private Currency currency;
    @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt;
    @Column(name="updated_at",nullable=false) private Instant updatedAt;
    protected PaymentTransactionEntity(){}
    public PaymentTransactionEntity(UUID id,UUID userId,UUID walletId,PaymentProviderType provider,BigDecimal amount,Currency currency,Instant now){
        this.id=id;this.userId=userId;this.walletId=walletId;this.provider=provider;this.amount=amount;this.currency=currency;this.status="CREATED";this.createdAt=now;this.updatedAt=now;
    }
    public UUID getId(){return id;} public UUID getUserId(){return userId;} public UUID getWalletId(){return walletId;} public PaymentProviderType getProvider(){return provider;}
    public String getProviderPaymentId(){return providerPaymentId;} public String getStatus(){return status;} public BigDecimal getAmount(){return amount;} public Currency getCurrency(){return currency;}
    public void attachProviderPayment(String id,String status,Instant now){this.providerPaymentId=id;this.status=status;this.updatedAt=now;}
    public void markStatus(String status,Instant now){this.status=status;this.updatedAt=now;}
    public void linkFinancialTransaction(UUID id,Instant now){this.financialTransactionId=id;this.updatedAt=now;}
}
