package com.rackpay.api.remittance;

import com.rackpay.api.domain.money.Currency;
import java.math.BigDecimal;

public interface PayoutProvider {
    PayoutProviderType type();
    boolean supportsRecipientVerification(String countryCode, Currency currency, PayoutMethod payoutMethod);
    RecipientVerification verifyRecipient(VerifyRecipientCommand command);
    PayoutResult createPayout(CreatePayoutCommand command);
    PayoutStatus getPayout(String providerTransferId);

    record VerifyRecipientCommand(String countryCode, Currency currency, PayoutMethod payoutMethod, String networkCode, String normalizedPhoneNumber) {}
    record RecipientVerification(boolean verified, String normalizedPhoneNumber, String verifiedName, String providerRecipientReference, String failureReason) {}
    record CreatePayoutCommand(String reference, BigDecimal amount, Currency currency, String countryCode, PayoutMethod payoutMethod, String networkCode, String normalizedPhoneNumber, String recipientName) {}
    record PayoutResult(String providerTransferId, String status) {}
    enum PayoutStatus { CREATED, PENDING, PROCESSING, COMPLETED, FAILED, CANCELLED, UNKNOWN }
}
