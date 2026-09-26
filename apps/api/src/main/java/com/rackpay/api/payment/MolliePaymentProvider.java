package com.rackpay.api.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rackpay.api.domain.money.Currency;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.Map;

@Component
public class MolliePaymentProvider implements PaymentProvider {
    private final RestClient client;
    private final String apiKey;
    private final String webhookSecret;
    private final ObjectMapper objectMapper;

    public MolliePaymentProvider(
        RestClient.Builder builder,
        @Value("${rackpay.payment.mollie.base-url:https://api.mollie.com/v2}") String baseUrl,
        @Value("$"+"{RACKPAY_MOLLIE_API_KEY:}") String apiKey,
        @Value("$"+"{RACKPAY_MOLLIE_WEBHOOK_SECRET:}") String webhookSecret,
        ObjectMapper objectMapper
    ) {
        this.apiKey = apiKey;
        this.webhookSecret = webhookSecret;
        this.client = builder.baseUrl(baseUrl).build();
        this.objectMapper = objectMapper;
    }

    @Override
    public PaymentProviderType type() {
        return PaymentProviderType.MOLLIE;
    }

    @Override
    public PaymentSession createPayment(CreatePaymentCommand c) {
        requireConfigured();
        Map<String, Object> body = Map.of(
            "amount", Map.of(
                "currency", c.currency().name(),
                "value", c.amount().setScale(c.currency().minorUnits()).toPlainString()
            ),
            "description", c.description(),
            "redirectUrl", c.returnUrl(),
            "webhookUrl", c.webhookUrl(),
            "metadata", Map.of("reference", c.reference())
        );

        Response r = client.post()
            .uri("/payments")
            .header("Authorization", "Bearer " + apiKey)
            .body(body)
            .retrieve()
            .body(Response.class);

        if (r == null || r.id() == null || r.links() == null || r.links().checkout() == null) {
            throw new IllegalStateException("Mollie returned an invalid payment response");
        }

        return new PaymentSession(r.id(), r.links().checkout().href());
    }

    @Override
    public PaymentStatus getPayment(String id) {
        return map(fetch(id).status());
    }

    @Override
    public void cancelPayment(String id) {
        requireConfigured();
        client.delete()
            .uri("/payments/{id}", id)
            .header("Authorization", "Bearer " + apiKey)
            .retrieve()
            .toBodilessEntity();
    }

    @Override
    public void refundPayment(String id, BigDecimal amount, Currency currency) {
        throw new UnsupportedOperationException("Mollie refunds require payment transaction integration");
    }

    @Override
    public WebhookResult handleWebhook(String payload, Map<String, String> headers) {
        requireConfigured();
        verifySignatureIfPresent(payload, headers.get("X-Mollie-Signature"));

        String paymentId;
        try {
            JsonNode node = objectMapper.readTree(payload);
            paymentId = node.isObject()
                ? (node.path("entityId").isTextual()
                    ? node.path("entityId").asText()
                    : node.path("id").asText())
                : node.asText();
        } catch (Exception e) {
            paymentId = parseClassicForm(payload);
        }

        if (paymentId == null || paymentId.isBlank()) {
            throw new IllegalArgumentException("Mollie webhook did not contain a payment id");
        }

        Response payment = fetch(paymentId);
        String eventId = "payment:" + paymentId + ":" + payment.status();
        String reference = payment.metadata() == null ? null : payment.metadata().reference();
        Currency currency = payment.amount() == null || payment.amount().currency() == null
            ? null
            : Currency.valueOf(payment.amount().currency().toUpperCase());

        return new WebhookResult(
            eventId,
            paymentId,
            map(payment.status()),
            "payment." + payment.status(),
            reference,
            parseAmount(payment.amount()),
            currency
        );
    }

    private String parseClassicForm(String payload) {
        for (String part : payload.split("&")) {
            String[] kv = part.split("=", 2);
            if (kv.length == 2 &&
                "id".equals(java.net.URLDecoder.decode(kv[0], java.nio.charset.StandardCharsets.UTF_8))) {
                return java.net.URLDecoder.decode(kv[1], java.nio.charset.StandardCharsets.UTF_8);
            }
        }
        throw new IllegalArgumentException("Mollie webhook did not contain a payment id");
    }

    private void verifySignatureIfPresent(String payload, String signature) {
        if (signature == null || signature.isBlank()) {
            return;
        }
        if (webhookSecret == null || webhookSecret.isBlank()) {
            throw new SecurityException("Mollie webhook secret is not configured");
        }
        try {
            String value = signature.startsWith("sha256=") ? signature.substring(7) : signature;
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(
                webhookSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                "HmacSHA256"
            ));
            String expected = java.util.HexFormat.of().formatHex(
                mac.doFinal(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8))
            );
            if (!java.security.MessageDigest.isEqual(
                expected.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                value.getBytes(java.nio.charset.StandardCharsets.UTF_8)
            )) {
                throw new SecurityException("Invalid Mollie webhook signature");
            }
        } catch (SecurityException e) {
            throw e;
        } catch (Exception e) {
            throw new SecurityException("Unable to verify Mollie webhook signature", e);
        }
    }

    private Response fetch(String id) {
        requireConfigured();
        Response r = client.get()
            .uri("/payments/{id}", id)
            .header("Authorization", "Bearer " + apiKey)
            .retrieve()
            .body(Response.class);
        if (r == null) {
            throw new IllegalStateException("Mollie payment not found");
        }
        return r;
    }

    private void requireConfigured() {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("Mollie API key is not configured");
        }
    }

    private PaymentStatus map(String s) {
        if (s == null) return PaymentStatus.UNKNOWN;
        return switch (s) {
            case "open" -> PaymentStatus.CREATED;
            case "pending" -> PaymentStatus.PENDING;
            case "authorized" -> PaymentStatus.REQUIRES_ACTION;
            case "paid" -> PaymentStatus.PAID;
            case "failed", "expired" -> PaymentStatus.FAILED;
            case "canceled" -> PaymentStatus.CANCELLED;
            case "refunded" -> PaymentStatus.REFUNDED;
            case "charged_back" -> PaymentStatus.CHARGED_BACK;
            default -> PaymentStatus.UNKNOWN;
        };
    }

    private BigDecimal parseAmount(Amount a) {
        return a == null || a.value() == null ? null : new BigDecimal(a.value());
    }

    private record Response(String id, String status, Links links, Metadata metadata, Amount amount) {}
    private record Amount(String currency, String value) {}
    private record Links(Checkout checkout) {}
    private record Checkout(String href) {}
    private record Metadata(String reference) {}
}
