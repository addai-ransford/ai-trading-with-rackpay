package com.rackpay.api.auth.core.service;

import com.rackpay.api.auth.adapters.in.web.RegistrationRequest;
import com.rackpay.api.auth.adapters.in.web.RegistrationResponse;
import com.rackpay.api.auth.adapters.out.keycloak.KeycloakAdminClient;
import com.rackpay.api.auth.core.exception.RegistrationException;
import com.rackpay.api.ledger.core.model.LedgerAccount;
import com.rackpay.api.payment.adapters.out.persistence.Status;
import com.rackpay.api.payment.adapters.out.persistence.Type;
import com.rackpay.api.payment.adapters.out.providers.stripe.Response;
import com.rackpay.api.wallet.core.model.Wallet;

import com.rackpay.api.ledger.core.model.LedgerAccountType;
import com.rackpay.api.shared.core.money.Currency;
import com.rackpay.api.ledger.adapters.out.persistence.LedgerAccountEntity;
import com.rackpay.api.ledger.adapters.out.persistence.LedgerAccountJpaRepository;
import com.rackpay.api.user.adapters.out.persistence.UserEntity;
import com.rackpay.api.user.adapters.out.persistence.UserJpaRepository;
import com.rackpay.api.user.adapters.out.persistence.UserStatus;
import com.rackpay.api.wallet.adapters.out.persistence.WalletBalanceEntity;
import com.rackpay.api.wallet.adapters.out.persistence.WalletBalanceJpaRepository;
import com.rackpay.api.wallet.adapters.out.persistence.WalletEntity;
import com.rackpay.api.wallet.adapters.out.persistence.WalletJpaRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Service
public class RegistrationService {
    private final KeycloakAdminClient keycloak;
    private final UserJpaRepository users;
    private final WalletJpaRepository wallets;
    private final WalletBalanceJpaRepository walletBalances;
    private final LedgerAccountJpaRepository ledgerAccounts;
    private final Currency defaultCurrency;

    public RegistrationService(KeycloakAdminClient keycloak, UserJpaRepository users, WalletJpaRepository wallets,
                               WalletBalanceJpaRepository walletBalances, LedgerAccountJpaRepository ledgerAccounts,
                               @Value("${rackpay.wallet.default-currency:EUR}") Currency defaultCurrency) {
        this.keycloak = keycloak;
        this.users = users;
        this.wallets = wallets;
        this.walletBalances = walletBalances;
        this.ledgerAccounts = ledgerAccounts;
        this.defaultCurrency = defaultCurrency;
    }

    @Transactional
    public RegistrationResponse register(RegistrationRequest request) {
        String email = request.email().trim().toLowerCase();
        if (users.existsByEmailIgnoreCase(email)) {
            throw new RegistrationException("email is already registered");
        }

        String keycloakSubject = keycloak.createUser(email, request.firstName().trim(), request.lastName().trim(),
            request.phone(), request.password());

        UUID userId = UUID.randomUUID();
        UUID walletId = UUID.randomUUID();
        UUID walletBalanceId = UUID.randomUUID();
        UUID ledgerAccountId = UUID.randomUUID();
        Instant now = Instant.now();

        try {
            users.saveAndFlush(new UserEntity(userId, keycloakSubject, email,
                request.firstName().trim(), request.lastName().trim(),
                request.phone(), UserStatus.ACTIVE, now));

            wallets.saveAndFlush(new WalletEntity(walletId, userId, now));

            ledgerAccounts.saveAndFlush(new LedgerAccountEntity(
                ledgerAccountId, "Wallet " + walletId + " " + defaultCurrency.name(),
                defaultCurrency, LedgerAccountType.LIABILITY, now));

            walletBalances.saveAndFlush(new WalletBalanceEntity(
                walletBalanceId, walletId, defaultCurrency, BigDecimal.ZERO, ledgerAccountId, now));

            return new RegistrationResponse(userId, walletId, defaultCurrency.name());
        } catch (DataIntegrityViolationException ex) {
            compensateKeycloakUser(keycloakSubject);
            throw new RegistrationException("registration could not be completed");
        } catch (RuntimeException ex) {
            compensateKeycloakUser(keycloakSubject);
            throw ex;
        }
    }

    private void compensateKeycloakUser(String keycloakSubject) {
        try {
            keycloak.deleteUser(keycloakSubject);
        } catch (RuntimeException ignored) {
            // The original registration failure remains the primary error.
            // A reconciliation job will be added before production rollout.
        }
    }
}
