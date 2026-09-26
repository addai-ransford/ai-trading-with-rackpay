package com.rackpay.api.remittance;

import com.rackpay.api.domain.money.Currency;
import com.rackpay.api.persistence.remittance.MobileMoneyNetworkEntity;
import com.rackpay.api.persistence.remittance.MobileMoneyNetworkJpaRepository;
import com.rackpay.api.persistence.remittance.RemittanceCountryEntity;
import com.rackpay.api.persistence.remittance.RemittanceCountryJpaRepository;
import com.rackpay.api.persistence.remittance.RemittanceRecipientEntity;
import com.rackpay.api.persistence.remittance.RemittanceRecipientJpaRepository;
import com.rackpay.api.persistence.user.CurrentUserService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;

@Service
public class RemittanceRecipientVerificationService {
    private final CurrentUserService currentUser;
    private final PayoutProviderRegistry providers;
    private final PhoneNumberNormalizer phoneNumbers;
    private final RemittanceCountryJpaRepository countries;
    private final MobileMoneyNetworkJpaRepository networks;
    private final RemittanceRecipientJpaRepository recipients;

    public RemittanceRecipientVerificationService(
        CurrentUserService currentUser,
        PayoutProviderRegistry providers,
        PhoneNumberNormalizer phoneNumbers,
        RemittanceCountryJpaRepository countries,
        MobileMoneyNetworkJpaRepository networks,
        RemittanceRecipientJpaRepository recipients
    ) {
        this.currentUser = currentUser;
        this.providers = providers;
        this.phoneNumbers = phoneNumbers;
        this.countries = countries;
        this.networks = networks;
        this.recipients = recipients;
    }

    @Transactional
    public VerificationResponse verify(Authentication authentication, VerificationRequest request) {
        var user = currentUser.requireUser(authentication);

        String countryCode = requireText(request.countryCode(), "countryCode").toUpperCase(Locale.ROOT);
        Currency currency = requireCurrency(request.currency());
        PayoutMethod payoutMethod = request.payoutMethod();

        RemittanceCountryEntity country = countries.findByCodeIgnoreCaseAndEnabledTrue(countryCode)
            .orElseThrow(() -> new IllegalArgumentException("Destination country is not enabled"));

        if (!country.isReceiveEnabled()) {
            throw new IllegalArgumentException("Destination country does not accept remittances");
        }

        if (!country.getCurrencyCode().equalsIgnoreCase(currency.name())) {
            throw new IllegalArgumentException("Currency does not match the destination country");
        }

        if (payoutMethod != PayoutMethod.MOBILE_MONEY) {
            throw new IllegalArgumentException("Only mobile money recipient verification is currently supported");
        }

        String networkCode = requireText(request.networkCode(), "networkCode").toUpperCase(Locale.ROOT);
        MobileMoneyNetworkEntity network = networks
            .findByCountryCodeIgnoreCaseAndCodeIgnoreCaseAndEnabledTrue(countryCode, networkCode)
            .orElseThrow(() -> new IllegalArgumentException("Mobile money network is not enabled for this country"));

        String normalized = phoneNumbers.normalize(country.getDialCode(), request.phoneNumber());

        PayoutProvider provider = providers.requireRecipientVerificationProvider(
            countryCode, currency, payoutMethod
        );

        PayoutProvider.RecipientVerification result = provider.verifyRecipient(
            new PayoutProvider.VerifyRecipientCommand(
                countryCode, currency, payoutMethod, network.getCode(), normalized
            )
        );

        if (!result.verified()) {
            return new VerificationResponse(
                false, null, countryCode, currency, payoutMethod, network.getCode(),
                result.normalizedPhoneNumber(), null, provider.type(),
                result.providerRecipientReference(), result.failureReason(), user.getId()
            );
        }

        Instant now = Instant.now();
        RemittanceRecipientEntity recipient = recipients
            .findActiveMobileRecipientForUpdate(
                user.getId(), countryCode, payoutMethod, normalized, network.getId()
            )
            .orElseGet(() -> new RemittanceRecipientEntity(
                java.util.UUID.randomUUID(),
                user.getId(),
                countryCode,
                payoutMethod,
                network.getId(),
                request.phoneNumber(),
                result.normalizedPhoneNumber(),
                result.verifiedName(),
                provider.type().name(),
                result.providerRecipientReference(),
                now
            ));

        if (recipient.getId() != null && recipient.getVerifiedAt() != null
            && !recipient.getVerifiedName().equals(result.verifiedName())) {
            recipient.refreshVerification(
                request.phoneNumber(),
                result.normalizedPhoneNumber(),
                result.verifiedName(),
                provider.type().name(),
                result.providerRecipientReference(),
                now
            );
        } else if (recipient.getVerifiedAt() != null) {
            recipient.refreshVerification(
                request.phoneNumber(),
                result.normalizedPhoneNumber(),
                result.verifiedName(),
                provider.type().name(),
                result.providerRecipientReference(),
                now
            );
        }

        recipients.save(recipient);

        return new VerificationResponse(
            true, recipient.getId(), countryCode, currency, payoutMethod, network.getCode(),
            result.normalizedPhoneNumber(), result.verifiedName(), provider.type(),
            result.providerRecipientReference(), null, user.getId()
        );
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }

    private static Currency requireCurrency(Currency value) {
        if (value == null) {
            throw new IllegalArgumentException("currency is required");
        }
        return value;
    }

    public record VerificationRequest(
        String countryCode, String countryDialCode, Currency currency,
        PayoutMethod payoutMethod, String networkCode, String phoneNumber
    ) {}

    public record VerificationResponse(
        boolean verified,
        java.util.UUID recipientId,
        String countryCode,
        Currency currency,
        PayoutMethod payoutMethod,
        String networkCode,
        String normalizedPhoneNumber,
        String verifiedName,
        PayoutProviderType verificationProvider,
        String providerRecipientReference,
        String failureReason,
        java.util.UUID userId
    ) {}
}
