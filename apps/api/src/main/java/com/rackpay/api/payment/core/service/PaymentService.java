package com.rackpay.api.payment.core.service;

import com.rackpay.api.payment.adapters.out.persistence.PaymentTransactionEntity;
import com.rackpay.api.payment.adapters.out.persistence.PaymentTransactionJpaRepository;
import com.rackpay.api.payment.core.model.PaymentProviderType;
import com.rackpay.api.payment.ports.out.PaymentProvider;
import com.rackpay.api.wallet.core.model.Wallet;

import com.rackpay.api.shared.core.money.Currency;
import com.rackpay.api.wallet.adapters.out.persistence.WalletEntity;
import com.rackpay.api.wallet.adapters.out.persistence.WalletJpaRepository;
import com.rackpay.api.user.adapters.out.persistence.CurrentUserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

@Service
public class PaymentService {
    private final CurrentUserService currentUser;
    private final WalletJpaRepository wallets;
    private final PaymentProviderService providerService;
    private final PaymentProviderRegistry registry;
    private final PaymentTransactionJpaRepository transactions;

    public PaymentService(
        CurrentUserService currentUser,
        WalletJpaRepository wallets,
        PaymentProviderService providerService,
        PaymentProviderRegistry registry,
        PaymentTransactionJpaRepository transactions
    ) {
        this.currentUser = currentUser;
        this.wallets = wallets;
        this.providerService = providerService;
        this.registry = registry;
        this.transactions = transactions;
    }

    @Transactional
    public PaymentResponse create(
        AuthenticationData auth,
        String reference,
        BigDecimal amount,
        Currency currency,
        String description,
        String returnUrl,
        String webhookUrl,
        String email
    ) {
        UUID userId = currentUser.requireUserId(auth.authentication());
        WalletEntity wallet = wallets.findByOwnerId(userId)
            .orElseThrow(() -> new IllegalStateException("RackPay wallet is not provisioned"));

        BigDecimal normalizedAmount;
        try {
            normalizedAmount = amount.setScale(currency.minorUnits(), RoundingMode.UNNECESSARY);
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException(
                "Amount has more fractional digits than supported by " + currency
            );
        }

        PaymentProviderType type = providerService.requireActiveProvider();
        PaymentProvider provider = registry.require(type);

        PaymentTransactionEntity tx = transactions.save(
            new PaymentTransactionEntity(
                UUID.randomUUID(),
                userId,
                wallet.getId(),
                type,
                normalizedAmount,
                currency,
                Instant.now()
            )
        );

        PaymentProvider.PaymentSession session = provider.createPayment(
            new PaymentProvider.CreatePaymentCommand(
                reference,
                normalizedAmount,
                currency,
                description,
                returnUrl,
                webhookUrl,
                email
            )
        );

        tx.attachProviderPayment(session.providerPaymentId(), "PENDING", Instant.now());
        return new PaymentResponse(
            tx.getId(),
            type,
            session.providerPaymentId(),
            session.checkoutUrl(),
            tx.getStatus()
        );
    }

    public record AuthenticationData(org.springframework.security.core.Authentication authentication) {}
    public record PaymentResponse(
        UUID transactionId,
        PaymentProviderType provider,
        String providerPaymentId,
        String checkoutUrl,
        String status
    ) {}
}
