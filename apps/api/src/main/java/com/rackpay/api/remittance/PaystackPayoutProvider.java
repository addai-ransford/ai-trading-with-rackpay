package com.rackpay.api.remittance;

import com.rackpay.api.domain.money.Currency;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class PaystackPayoutProvider implements PayoutProvider {
    private final String secretKey;

    public PaystackPayoutProvider(@Value("\${rackpay.payout.paystack.secret-key:}") String secretKey) {
        this.secretKey = secretKey;
    }

    @Override
    public PayoutProviderType type() { return PayoutProviderType.PAYSTACK; }

    @Override
    public boolean supportsRecipientVerification(String countryCode, Currency currency, PayoutMethod payoutMethod) {
        return false;
    }

    @Override
    public RecipientVerification verifyRecipient(VerifyRecipientCommand command) {
        throw new UnsupportedOperationException("Paystack does not expose mobile-money name resolution through its recipient API");
    }

    @Override
    public PayoutResult createPayout(CreatePayoutCommand command) {
        if (secretKey.isBlank()) throw new IllegalStateException("Paystack payout credentials are not configured");
        throw new UnsupportedOperationException("Paystack payout execution is enabled in the next remittance execution step");
    }

    @Override
    public PayoutStatus getPayout(String providerTransferId) {
        throw new UnsupportedOperationException("Paystack payout status is enabled in the next remittance execution step");
    }
}
