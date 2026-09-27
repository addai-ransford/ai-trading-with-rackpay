package com.rackpay.api.remittance;

import com.rackpay.api.domain.money.Currency;
import java.math.BigDecimal;

public interface PayoutProvider {
    PayoutProviderType type();
    boolean supportsRecipientVerification(String countryCode, Currency currency, PayoutMethod payoutMethod);

    default boolean supportsPayout(CreatePayoutCommand command) {
        return true;
    }

    RecipientVerification verifyRecipient(VerifyRecipientCommand command);
    PayoutResult createPayout(CreatePayoutCommand command);
    PayoutStatus getPayout(String providerTransferId);

    default PayoutResult findPayoutByReference(String reference) { return null; }

    record VerifyRecipientCommand(String countryCode, Currency currency, PayoutMethod payoutMethod, String networkCode, String normalizedPhoneNumber) {}
    record RecipientVerification(boolean verified, String normalizedPhoneNumber, String verifiedName, String providerRecipientReference, String failureReason) {}
    record CreatePayoutCommand(String reference, BigDecimal amount, Currency currency, Currency sourceCurrency, String countryCode, PayoutMethod payoutMethod, String networkCode, String normalizedPhoneNumber, String recipientName) {}
    record PayoutResult(String providerTransferId, String status) {}
    enum PayoutStatus { CREATED, PENDING, PROCESSING, COMPLETED, FAILED, CANCELLED, UNKNOWN }
}
