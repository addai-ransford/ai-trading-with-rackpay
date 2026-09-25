package com.rackpay.api.service;

import com.rackpay.api.domain.money.Money;
import com.rackpay.api.domain.transaction.IdempotencyKey;
import com.rackpay.api.payment.PaymentProvider;
import com.rackpay.api.payment.PaymentProviderRegistry;
import com.rackpay.api.payment.PaymentProviderType;
import com.rackpay.api.persistence.payment.*;
import com.rackpay.api.persistence.wallet.WalletBalanceService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Map;

@Service
public class PaymentWebhookService {
    private final PaymentProviderRegistry registry;
    private final PaymentProviderEventJpaRepository events;
    private final PaymentTransactionJpaRepository payments;
    private final WalletBalanceService balances;
    private final WalletCreditService walletCredit;
    private final PaymentSettlementAccountService settlementAccounts;

    public PaymentWebhookService(PaymentProviderRegistry registry,PaymentProviderEventJpaRepository events,
                                  PaymentTransactionJpaRepository payments,WalletBalanceService balances,
                                  WalletCreditService walletCredit,PaymentSettlementAccountService settlementAccounts){
        this.registry=registry;this.events=events;this.payments=payments;this.balances=balances;
        this.walletCredit=walletCredit;this.settlementAccounts=settlementAccounts;
    }

    @Transactional
    public void handle(PaymentProviderType providerType,String payload,Map<String,String> headers){
        PaymentProvider provider=registry.require(providerType);
        PaymentProvider.WebhookResult result=provider.handleWebhook(payload,headers);
        if(result.eventId()==null||result.eventId().isBlank())throw new IllegalArgumentException("Provider webhook has no event id");

        String lockKey=providerType.name()+":"+result.eventId();
        events.lockEvent(lockKey);
        if(events.findByProviderAndEventId(providerType,result.eventId()).map(e->"PROCESSED".equals(e.getStatus())).orElse(false)) return;

        PaymentTransactionEntity payment=payments.findByProviderAndProviderPaymentIdForUpdate(providerType,result.providerPaymentId())
            .orElseThrow(()->new IllegalArgumentException("Unknown payment transaction: "+result.providerPaymentId()));

        PaymentProviderEventEntity event=events.findByProviderAndEventId(providerType,result.eventId())
            .orElseGet(()->events.save(new PaymentProviderEventEntity(
                java.util.UUID.randomUUID(),providerType,result.eventId(),result.providerPaymentId(),result.rawEventType(),Instant.now()
            )));

        if(result.status()==PaymentProvider.PaymentStatus.PAID){
            if(result.amount()==null||result.currency()==null)throw new IllegalStateException("Provider webhook did not contain a verified amount and currency");
            if(payment.getAmount().compareTo(result.amount())!=0||payment.getCurrency()!=result.currency())
                throw new SecurityException("Provider payment amount or currency does not match RackPay transaction");
            balances.openBalance(payment.getWalletId(),payment.getCurrency());
            java.util.UUID clearingAccount=settlementAccounts.requireAccount(providerType,payment.getCurrency());
            String key="payment-credit:"+providerType.name()+":"+payment.getProviderPaymentId();
            String hash=sha256(payload);
            var financial=walletCredit.credit(new IdempotencyKey(key),hash,payment.getWalletId(),clearingAccount,
                new Money(payment.getAmount(),payment.getCurrency()));
            payment.markStatus("PAID",Instant.now());
            payment.linkFinancialTransaction(financial.getId(),Instant.now());
        } else if(result.status()==PaymentProvider.PaymentStatus.FAILED){
            payment.markStatus("FAILED",Instant.now());
        } else if(result.status()==PaymentProvider.PaymentStatus.CANCELLED){
            payment.markStatus("CANCELLED",Instant.now());
        } else {
            payment.markStatus(result.status().name(),Instant.now());
        }
        event.markProcessed(Instant.now());
    }

    private String sha256(String value){
        try{return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}
        catch(Exception e){throw new IllegalStateException("Unable to hash provider event",e);}
    }
}
