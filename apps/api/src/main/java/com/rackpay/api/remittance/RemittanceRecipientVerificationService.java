package com.rackpay.api.remittance;

import com.rackpay.api.domain.money.Currency;
import com.rackpay.api.persistence.user.CurrentUserService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
public class RemittanceRecipientVerificationService {
    private final CurrentUserService currentUser;
    private final PayoutProviderRegistry providers;
    private final PhoneNumberNormalizer phoneNumbers;

    public RemittanceRecipientVerificationService(CurrentUserService currentUser, PayoutProviderRegistry providers, PhoneNumberNormalizer phoneNumbers) {
        this.currentUser = currentUser;
        this.providers = providers;
        this.phoneNumbers = phoneNumbers;
    }

    public VerificationResponse verify(Authentication authentication, VerificationRequest request) {
        var user = currentUser.requireUser(authentication);
        String normalized = phoneNumbers.normalize(request.countryDialCode(), request.phoneNumber());

        PayoutProvider provider = providers.requireRecipientVerificationProvider(
            request.countryCode(), request.currency(), request.payoutMethod()
        );

        PayoutProvider.RecipientVerification result = provider.verifyRecipient(
            new PayoutProvider.VerifyRecipientCommand(
                request.countryCode(), request.currency(), request.payoutMethod(), request.networkCode(), normalized
            )
        );

        return new VerificationResponse(
            result.verified(), request.countryCode().toUpperCase(), request.currency(),
            request.payoutMethod(), request.networkCode(), result.normalizedPhoneNumber(),
            result.verifiedName(), provider.type(), result.providerRecipientReference(),
            result.failureReason(), user.getId()
        );
    }

    public record VerificationRequest(
        String countryCode, String countryDialCode, Currency currency,
        PayoutMethod payoutMethod, String networkCode, String phoneNumber
    ) {}

    public record VerificationResponse(
        boolean verified, String countryCode, Currency currency, PayoutMethod payoutMethod,
        String networkCode, String normalizedPhoneNumber, String verifiedName,
        PayoutProviderType verificationProvider, String providerRecipientReference,
        String failureReason, java.util.UUID userId
    ) {}
}
