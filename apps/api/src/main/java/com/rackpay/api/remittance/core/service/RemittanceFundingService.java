package com.rackpay.api.remittance.core.service;

import com.rackpay.api.payment.adapters.out.persistence.Status;
import com.rackpay.api.payment.adapters.out.providers.mollie.Amount;
import com.rackpay.api.payment.adapters.out.providers.stripe.Response;
import com.rackpay.api.shared.core.money.Currency;
import com.rackpay.api.shared.core.service.must;
import com.rackpay.api.shared.core.transaction.FinancialTransaction;
import com.rackpay.api.shared.core.transaction.TransactionId;
import com.rackpay.api.wallet.core.model.Wallet;

import com.rackpay.api.shared.core.money.Money;
import com.rackpay.api.shared.core.transaction.IdempotencyKey;
import com.rackpay.api.remittance.adapters.out.persistence.RemittanceEntity;
import com.rackpay.api.remittance.adapters.out.persistence.RemittanceQuoteEntity;
import com.rackpay.api.remittance.adapters.out.persistence.RemittanceQuoteJpaRepository;
import com.rackpay.api.remittance.adapters.out.persistence.RemittanceJpaRepository;
import com.rackpay.api.user.adapters.out.persistence.CurrentUserService;
import com.rackpay.api.wallet.adapters.out.persistence.WalletEntity;
import com.rackpay.api.wallet.adapters.out.persistence.WalletJpaRepository;
import com.rackpay.api.wallet.core.service.WalletDebitService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.UUID;

@Service
public class RemittanceFundingService {
    private final CurrentUserService currentUser;
    private final WalletJpaRepository wallets;
    private final RemittanceQuoteJpaRepository quotes;
    private final RemittanceJpaRepository remittances;
    private final RemittanceClearingAccountService clearingAccounts;
    private final WalletDebitService walletDebit;

    public RemittanceFundingService(
        CurrentUserService currentUser,
        WalletJpaRepository wallets,
        RemittanceQuoteJpaRepository quotes,
        RemittanceJpaRepository remittances,
        RemittanceClearingAccountService clearingAccounts,
        WalletDebitService walletDebit
    ) {
        this.currentUser = currentUser;
        this.wallets = wallets;
        this.quotes = quotes;
        this.remittances = remittances;
        this.clearingAccounts = clearingAccounts;
        this.walletDebit = walletDebit;
    }

    @Transactional
    public FundingResponse fund(Authentication authentication, UUID quoteId, String idempotencyKey) {
        UUID userId = currentUser.requireUserId(authentication);
        requireIdempotencyKey(idempotencyKey);

        RemittanceQuoteEntity quote = quotes.findByIdForUpdate(quoteId)
            .orElseThrow(() -> new IllegalArgumentException("remittance quote not found"));

        String requestHash = sha256(quoteId + "|" + userId);

        RemittanceEntity existing =
            remittances.findByUserIdAndIdempotencyKeyForUpdate(userId, idempotencyKey)
                .orElse(null);

        if (existing != null) {
            if (existing.getRequestHash() == null
                || !existing.getRequestHash().equalsIgnoreCase(requestHash)) {
                throw new IllegalArgumentException(
                    "idempotency key was already used for a different remittance request"
                );
            }
            return response(existing);
        }

        if (!quote.getUserId().equals(userId)) {
            throw new IllegalArgumentException("remittance quote does not belong to the authenticated user");
        }
        if (quote.getExpiresAt().isBefore(Instant.now())) {
            throw new IllegalStateException("remittance quote has expired");
        }
        if (quote.getRemittanceId() != null || quote.getConsumedAt() != null) {
            throw new IllegalStateException("remittance quote has already been used");
        }

        WalletEntity wallet = wallets.findByOwnerId(userId)
            .orElseThrow(() -> new IllegalStateException("wallet is not provisioned"));

        BigDecimal totalDebit = quote.getSourceAmount().add(quote.getFeeAmount());
        if (totalDebit.signum() <= 0) {
            throw new IllegalStateException("remittance funding amount is invalid");
        }

        var clearingAccount = clearingAccounts.require(quote.getSourceCurrency());
        UUID remittanceId = UUID.randomUUID();
        Instant now = Instant.now();

        RemittanceEntity remittance = new RemittanceEntity(
            remittanceId,
            userId,
            wallet.getId(),
            quote.getCorridorId(),
            quote.getRecipientId(),
            quote.getSourceAmount(),
            quote.getSourceCurrency(),
            quote.getDestinationAmount(),
            quote.getDestinationCurrency(),
            quote.getFeeAmount(),
            quote.getFeeCurrency(),
            quote.getFxRate(),
            RemittanceEntity.Status.CREATED,
            idempotencyKey,
            requestHash,
            now,
            now
        );

        remittances.saveAndFlush(remittance);

        var transaction = walletDebit.debit(
            new IdempotencyKey("remittance:fund:" + remittanceId),
            sha256(remittanceId + "|" + totalDebit.toPlainString() + "|" + quote.getSourceCurrency().name()),
            wallet.getId(),
            clearingAccount.getId(),
            new Money(totalDebit, quote.getSourceCurrency())
        );

        quote.consume(remittanceId, now);
        remittance.markFundsReserved(transaction.getId(), clearingAccount.getId(), Instant.now());

        return response(remittance);
    }

    private FundingResponse response(RemittanceEntity remittance) {
        return new FundingResponse(
            remittance.getId(),
            remittance.getStatus(),
            remittance.getSourceAmount(),
            remittance.getFeeAmount(),
            remittance.getSourceAmount().add(remittance.getFeeAmount()),
            remittance.getSourceCurrency(),
            remittance.getDestinationAmount(),
            remittance.getDestinationCurrency(),
            remittance.getFundingFinancialTransactionId()
        );
    }

    private static void requireIdempotencyKey(String key) {
        if (key == null || key.isBlank() || key.length() > 255) {
            throw new IllegalArgumentException("Idempotency-Key must contain 1-255 characters");
        }
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(64);
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    public record FundingResponse(
        UUID remittanceId,
        RemittanceEntity.Status status,
        BigDecimal sourceAmount,
        BigDecimal feeAmount,
        BigDecimal totalDebit,
        com.rackpay.api.shared.core.money.Currency sourceCurrency,
        BigDecimal destinationAmount,
        com.rackpay.api.shared.core.money.Currency destinationCurrency,
        UUID fundingFinancialTransactionId
    ) {}
}
