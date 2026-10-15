package com.rackpay.api.remittance.core.service;

import java.time.Instant;
import java.util.Locale;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.rackpay.api.remittance.adapters.out.persistence.MobileMoneyNetworkEntity;
import com.rackpay.api.remittance.adapters.out.persistence.MobileMoneyNetworkJpaRepository;
import com.rackpay.api.remittance.adapters.out.persistence.RemittanceCountryEntity;
import com.rackpay.api.remittance.adapters.out.persistence.RemittanceCountryJpaRepository;
import com.rackpay.api.remittance.adapters.out.persistence.RemittanceRecipientEntity;
import com.rackpay.api.remittance.adapters.out.persistence.RemittanceRecipientJpaRepository;
import com.rackpay.api.remittance.core.model.PayoutMethod;
import com.rackpay.api.remittance.core.model.PayoutProviderType;
import com.rackpay.api.remittance.ports.out.PayoutProvider;
import com.rackpay.api.shared.core.money.Currency;
import com.rackpay.api.user.adapters.out.persistence.CurrentUserService;

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

        PayoutProvider provider;
        try {
            provider = providers.requireRecipientVerificationProvider(countryCode, currency, payoutMethod);
        } catch (IllegalStateException exception) {
            return new VerificationResponse(
                false, null, countryCode, currency, payoutMethod, network.getCode(),
                normalized, null, null, null,
                "No configured payout provider can verify this destination and network.", user.getId(), null
            );
        }

        PayoutProvider.RecipientVerification result = provider.verifyRecipient(
            new PayoutProvider.VerifyRecipientCommand(
                countryCode, currency, payoutMethod, network.getCode(), normalized
            )
        );

        if (!result.verified()) {
            return new VerificationResponse(
                false, null, countryCode, currency, payoutMethod, network.getCode(),
                result.normalizedPhoneNumber(), null, provider.type(),
                result.providerRecipientReference(), result.failureReason(), user.getId(), null
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

        // A successful provider lookup upgrades an earlier sender-entered recipient
        // and refreshes the stored details for an already-verified recipient.
        recipient.refreshVerification(
            request.phoneNumber(),
            result.normalizedPhoneNumber(),
            result.verifiedName(),
            provider.type().name(),
            result.providerRecipientReference(),
            now
        );

        recipients.save(recipient);

        return new VerificationResponse(
            true, recipient.getId(), countryCode, currency, payoutMethod, network.getCode(),
            result.normalizedPhoneNumber(), result.verifiedName(), provider.type(),
            result.providerRecipientReference(), null, user.getId(), result.verifiedName()
        );
    }

    @Transactional
    public VerificationResponse addSenderProvidedRecipient(Authentication authentication, ManualRecipientRequest request) {
        if (request == null) throw new IllegalArgumentException("recipient details are required");
        // Recheck with the provider server-side so this fallback is only used when
        // the provider still cannot resolve the supplied mobile money number.
        VerificationResponse providerCheck = verify(authentication, new VerificationRequest(
            request.countryCode(), null, request.currency(), request.payoutMethod(),
            request.networkCode(), request.phoneNumber()
        ));
        if (providerCheck.verified()) return providerCheck;

        var user = currentUser.requireUser(authentication);
        String countryCode = requireText(request.countryCode(), "countryCode").toUpperCase(Locale.ROOT);
        Currency currency = requireCurrency(request.currency());
        RemittanceCountryEntity country = countries.findByCodeIgnoreCaseAndEnabledTrue(countryCode)
            .orElseThrow(() -> new IllegalArgumentException("Destination country is not enabled"));
        if (!country.isReceiveEnabled() || !country.getCurrencyCode().equalsIgnoreCase(currency.name())) {
            throw new IllegalArgumentException("Destination country or currency is not supported");
        }
        if (request.payoutMethod() != PayoutMethod.MOBILE_MONEY) {
            throw new IllegalArgumentException("Only mobile money recipients are currently supported");
        }
        String networkCode = requireText(request.networkCode(), "networkCode").toUpperCase(Locale.ROOT);
        MobileMoneyNetworkEntity network = networks
            .findByCountryCodeIgnoreCaseAndCodeIgnoreCaseAndEnabledTrue(countryCode, networkCode)
            .orElseThrow(() -> new IllegalArgumentException("Mobile money network is not enabled for this country"));
        String name = requireText(request.recipientName(), "recipientName");
        if (name.length() > 255) throw new IllegalArgumentException("recipientName must be 255 characters or fewer");
        String normalized = phoneNumbers.normalize(country.getDialCode(), request.phoneNumber());
        Instant now = Instant.now();
        RemittanceRecipientEntity recipient = RemittanceRecipientEntity.senderProvided(
            java.util.UUID.randomUUID(), user.getId(), countryCode, request.payoutMethod(), network.getId(),
            request.phoneNumber(), normalized, name, now
        );
        recipients.save(recipient);
        return new VerificationResponse(
            false, recipient.getId(), countryCode, currency, request.payoutMethod(), network.getCode(),
            normalized, null, null, null, "Name provided by sender; not verified by the provider.", user.getId(), name
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

    public record ManualRecipientRequest(
        String countryCode, Currency currency, PayoutMethod payoutMethod,
        String networkCode, String phoneNumber, String recipientName
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
        java.util.UUID userId,
        String recipientName
    ) {}
}
