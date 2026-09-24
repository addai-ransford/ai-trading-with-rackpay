package com.rackpay.api.auth;

import com.rackpay.api.domain.ledger.LedgerAccountType;
import com.rackpay.api.domain.money.Currency;
import com.rackpay.api.persistence.ledger.LedgerAccountEntity;
import com.rackpay.api.persistence.ledger.LedgerAccountJpaRepository;
import com.rackpay.api.persistence.user.UserEntity;
import com.rackpay.api.persistence.user.UserJpaRepository;
import com.rackpay.api.persistence.user.UserStatus;
import com.rackpay.api.persistence.wallet.WalletEntity;
import com.rackpay.api.persistence.wallet.WalletJpaRepository;
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
    private final LedgerAccountJpaRepository ledgerAccounts;
    private final Currency defaultCurrency;

    public RegistrationService(
        KeycloakAdminClient keycloak,
        UserJpaRepository users,
        WalletJpaRepository wallets,
        LedgerAccountJpaRepository ledgerAccounts,
        @Value("${rackpay.wallet.default-currency:EUR}") Currency defaultCurrency
    ) {
        this.keycloak = keycloak;
        this.users = users;
        this.wallets = wallets;
        this.ledgerAccounts = ledgerAccounts;
        this.defaultCurrency = defaultCurrency;
    }

    @Transactional
    public RegistrationResponse register(RegistrationRequest request) {
        String email = request.email().trim().toLowerCase();

        if (users.existsByEmailIgnoreCase(email)) {
            throw new RegistrationException("email is already registered");
        }

        String keycloakSubject = keycloak.createUser(
            email,
            request.firstName().trim(),
            request.lastName().trim(),
            request.phone(),
            request.password()
        );

        UUID userId = UUID.randomUUID();
        UUID walletId = UUID.randomUUID();
        UUID ledgerAccountId = UUID.randomUUID();
        Instant now = Instant.now();

        try {
            users.save(new UserEntity(
                userId, keycloakSubject, email,
                request.firstName().trim(), request.lastName().trim(),
                request.phone(), UserStatus.ACTIVE, now
            ));

            ledgerAccounts.save(new LedgerAccountEntity(
                ledgerAccountId,
                "Wallet " + walletId,
                defaultCurrency,
                LedgerAccountType.ASSET,
                now
            ));

            wallets.save(new WalletEntity(
                walletId, userId, defaultCurrency, BigDecimal.ZERO, now, ledgerAccountId
            ));

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
