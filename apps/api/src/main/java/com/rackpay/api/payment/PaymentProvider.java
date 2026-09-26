package com.rackpay.api.payment;

import com.rackpay.api.domain.money.Currency;
import java.math.BigDecimal;
import java.util.Map;

public interface PaymentProvider {
    PaymentProviderType type();
    PaymentSession createPayment(CreatePaymentCommand command);
    PaymentStatus getPayment(String providerPaymentId);
    void cancelPayment(String providerPaymentId);
    void refundPayment(String providerPaymentId, BigDecimal amount, Currency currency);
    WebhookResult handleWebhook(String payload, Map<String, String> headers);

    record CreatePaymentCommand(String reference, BigDecimal amount, Currency currency,
                                String description, String returnUrl, String webhookUrl,
                                String customerEmail) {}
    record PaymentSession(String providerPaymentId, String checkoutUrl) {}
    enum PaymentStatus { CREATED, PENDING, REQUIRES_ACTION, PAID, FAILED, CANCELLED, REFUNDED, CHARGED_BACK, UNKNOWN }
    record WebhookResult(
        String eventId,
        String providerPaymentId,
        PaymentStatus status,
        String rawEventType,
        String reference,
        BigDecimal amount,
        Currency currency
    ) {}
}
