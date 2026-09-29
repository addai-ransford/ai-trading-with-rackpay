package com.rackpay.api.remittance;

import com.rackpay.api.remittance.core.model.*;
import com.rackpay.api.remittance.core.service.*;
import com.rackpay.api.remittance.ports.out.*;

import com.rackpay.api.shared.core.money.Currency;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class PayoutProviderRegistryTest {

    @Test
    void selectsNextProviderInConfiguredOrder() {
        PayoutProvider flutterwave = provider(PayoutProviderType.FLUTTERWAVE, true);
        PayoutProvider paystack = provider(PayoutProviderType.PAYSTACK, true);

        MockEnvironment environment = new MockEnvironment()
            .withProperty("rackpay.payout.provider-order", "PAYSTACK,FLUTTERWAVE");

        PayoutProviderRegistry registry =
            new PayoutProviderRegistry(List.of(flutterwave, paystack), environment);

        PayoutProvider.CreatePayoutCommand command = command();

        PayoutProvider next = registry.nextEligible(
            PayoutProviderType.FLUTTERWAVE,
            Set.of(PayoutProviderType.FLUTTERWAVE),
            command
        );

        assertNotNull(next);
        assertEquals(PayoutProviderType.PAYSTACK, next.type());
    }

    @Test
    void doesNotReuseAnAlreadyAttemptedProvider() {
        PayoutProvider flutterwave = provider(PayoutProviderType.FLUTTERWAVE, true);
        PayoutProvider paystack = provider(PayoutProviderType.PAYSTACK, true);

        PayoutProviderRegistry registry =
            new PayoutProviderRegistry(List.of(flutterwave, paystack), new MockEnvironment());

        PayoutProvider next = registry.nextEligible(
            PayoutProviderType.FLUTTERWAVE,
            Set.of(PayoutProviderType.FLUTTERWAVE, PayoutProviderType.PAYSTACK),
            command()
        );

        assertNull(next);
    }

    @Test
    void skipsProviderThatDoesNotSupportPayout() {
        PayoutProvider flutterwave = provider(PayoutProviderType.FLUTTERWAVE, true);
        PayoutProvider paystack = provider(PayoutProviderType.PAYSTACK, false);

        PayoutProviderRegistry registry =
            new PayoutProviderRegistry(List.of(flutterwave, paystack), new MockEnvironment());

        PayoutProvider next = registry.nextEligible(
            PayoutProviderType.FLUTTERWAVE,
            Set.of(PayoutProviderType.FLUTTERWAVE),
            command()
        );

        assertNull(next);
    }

    private static PayoutProvider provider(PayoutProviderType type, boolean supportsPayout) {
        return new PayoutProvider() {
            @Override public PayoutProviderType type() { return type; }
            @Override public boolean supportsRecipientVerification(
                String countryCode, Currency currency, PayoutMethod payoutMethod) {
                return false;
            }
            @Override public boolean supportsPayout(CreatePayoutCommand command) {
                return supportsPayout;
            }
            @Override public RecipientVerification verifyRecipient(VerifyRecipientCommand command) {
                throw new UnsupportedOperationException();
            }
            @Override public PayoutResult createPayout(CreatePayoutCommand command) {
                throw new UnsupportedOperationException();
            }
            @Override public PayoutStatus getPayout(String providerTransferId) {
                throw new UnsupportedOperationException();
            }
        };
    }

    private static PayoutProvider.CreatePayoutCommand command() {
        return new PayoutProvider.CreatePayoutCommand(
            "rp-test-1",
            new BigDecimal("100.00"),
            Currency.GHS,
            Currency.EUR,
            "GH",
            PayoutMethod.MOBILE_MONEY,
            "MTN",
            "+233241234567",
            "Test Recipient"
        );
    }
}
