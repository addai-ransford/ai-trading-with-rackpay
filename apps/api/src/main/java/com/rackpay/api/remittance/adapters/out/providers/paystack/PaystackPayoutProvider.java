package com.rackpay.api.remittance.adapters.out.providers.paystack;

import com.rackpay.api.remittance.core.model.PayoutMethod;
import com.rackpay.api.remittance.core.model.PayoutProviderType;
import com.rackpay.api.remittance.ports.out.PayoutProvider;
import com.rackpay.api.remittance.ports.out.PayoutProvider.CreatePayoutCommand;
import com.rackpay.api.remittance.ports.out.PayoutProvider.PayoutResult;
import com.rackpay.api.remittance.ports.out.PayoutProvider.PayoutStatus;
import com.rackpay.api.remittance.ports.out.PayoutProvider.RecipientVerification;
import com.rackpay.api.remittance.ports.out.PayoutProvider.VerifyRecipientCommand;
import com.rackpay.api.shared.core.money.Currency;
import com.rackpay.api.shared.infrastructure.retry.ProviderRetryClassifier;
import com.rackpay.api.shared.infrastructure.retry.ProviderRetryExecutor;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@Component
public class PaystackPayoutProvider implements PayoutProvider {
    private final RestClient client;
    private final String secretKey;
    private final ProviderRetryExecutor retryExecutor;

    public PaystackPayoutProvider(
        RestClient.Builder builder,
        @Value("$"+"{rackpay.payout.paystack.base-url:https://api.paystack.co}") String baseUrl,
        @Value("$"+"{rackpay.payout.paystack.secret-key:}") String secretKey,
        ProviderRetryExecutor retryExecutor
    ) {
        this.client = builder.baseUrl(baseUrl).build();
        this.secretKey = secretKey;
        this.retryExecutor = retryExecutor;
    }

    @Override public PayoutProviderType type() { return PayoutProviderType.PAYSTACK; }

    @Override
    public boolean supportsRecipientVerification(String countryCode, Currency currency, PayoutMethod payoutMethod) {
        return false;
    }

    @Override
    public boolean supportsPayout(CreatePayoutCommand command) {
        return !secretKey.isBlank()
            && command.payoutMethod() == PayoutMethod.MOBILE_MONEY
            && ((("GH".equalsIgnoreCase(command.countryCode()) && command.currency() == Currency.GHS)
                || ("KE".equalsIgnoreCase(command.countryCode()) && command.currency() == Currency.KES)))
            && supportedNetwork(command.networkCode());
    }

    @Override
    public RecipientVerification verifyRecipient(VerifyRecipientCommand command) {
        throw new UnsupportedOperationException("Paystack recipient verification is not used; RackPay requires a verified recipient name");
    }

    @Override
    public PayoutResult createPayout(CreatePayoutCommand command) {
        if (!supportsPayout(command)) throw new IllegalArgumentException("Paystack does not support this payout");

        String recipientCode = createRecipient(command);
        long minorAmount = command.amount().movePointRight(command.currency().minorUnits())
            .setScale(0, RoundingMode.UNNECESSARY).longValueExact();

        Map<String, Object> body = new HashMap<>();
        body.put("source", "balance");
        body.put("amount", minorAmount);
        body.put("recipient", recipientCode);
        body.put("reference", normalizeReference(command.reference()));
        body.put("reason", "RackPay remittance " + command.reference());
        body.put("currency", command.currency().name());

        JsonNode response = client.post().uri("/transfer")
            .headers(h -> h.setBearerAuth(secretKey))
            .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON)
            .body(body).retrieve().body(JsonNode.class);

        if (response == null) throw new IllegalStateException("Paystack returned an empty payout response");

        JsonNode data = response.path("data");
        String id = data.path("id").asText(null);
        String transferCode = data.path("transfer_code").asText(null);
        String status = data.path("status").asText(null);

        if (!response.path("status").asBoolean(false) || (id == null && transferCode == null)) {
            return new PayoutResult(id != null ? id : transferCode,
                "FAILED:" + response.path("message").asText("Paystack payout creation failed"));
        }
        return new PayoutResult(id != null ? id : transferCode, status == null ? "PENDING" : status);
    }

    @Override
    public PayoutStatus getPayout(String providerTransferId) {
        return retryExecutor.execute(
            "PAYSTACK",
            "payout-status",
            () -> {
                JsonNode response = client.get().uri("/transfer/{idOrCode}", providerTransferId)
                    .headers(h -> h.setBearerAuth(secretKey)).accept(MediaType.APPLICATION_JSON)
                    .retrieve().body(JsonNode.class);
                if (response == null || !response.path("status").asBoolean(false)) return PayoutStatus.UNKNOWN;
                return mapStatus(response.path("data").path("status").asText(null));
            },
            ProviderRetryClassifier::isTransient
        );
    }

    @Override
    public PayoutResult findPayoutByReference(String reference) {
        return retryExecutor.execute(
            "PAYSTACK",
            "payout-reconciliation",
            () -> {
                JsonNode response = client.get().uri("/transfer/verify/{reference}", normalizeReference(reference))
                    .headers(h -> h.setBearerAuth(secretKey)).accept(MediaType.APPLICATION_JSON)
                    .retrieve().body(JsonNode.class);
                if (response == null || !response.path("status").asBoolean(false)) return null;

                JsonNode data = response.path("data");
                String id = data.path("id").asText(null);
                String transferCode = data.path("transfer_code").asText(null);
                if (id == null && transferCode == null) return null;
                return new PayoutResult(id != null ? id : transferCode, data.path("status").asText("UNKNOWN"));
            },
            ProviderRetryClassifier::isTransient
        );
    }

    private String createRecipient(CreatePayoutCommand command) {
        Map<String, Object> body = Map.of(
            "type", "mobile_money",
            "name", command.recipientName(),
            "account_number", command.normalizedPhoneNumber().replace("+", ""),
            "bank_code", networkCode(command.networkCode()),
            "currency", command.currency().name()
        );

        JsonNode response = client.post().uri("/transferrecipient")
            .headers(h -> h.setBearerAuth(secretKey))
            .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON)
            .body(body).retrieve().body(JsonNode.class);

        if (response == null || !response.path("status").asBoolean(false)) {
            throw new IllegalStateException("Paystack recipient creation failed");
        }
        String code = response.path("data").path("recipient_code").asText(null);
        if (code == null || code.isBlank()) throw new IllegalStateException("Paystack did not return a recipient code");
        return code;
    }

    private static boolean supportedNetwork(String value) {
        if (value == null) return false;
        return switch (value.toUpperCase(Locale.ROOT)) {
            case "MTN", "AIRTELTIGO", "TELECEL", "MPESA" -> true;
            default -> false;
        };
    }

    private static String networkCode(String value) {
        return switch (value.toUpperCase(Locale.ROOT)) {
            case "AIRTELTIGO" -> "ATL";
            case "TELECEL" -> "VOD";
            case "MTN" -> "MTN";
            case "MPESA" -> "MPESA";
            default -> throw new IllegalArgumentException("Unsupported Paystack mobile money network: " + value);
        };
    }

    private static String normalizeReference(String value) {
        String normalized = value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "-");
        if (normalized.length() < 16) normalized = "rp_" + normalized;
        return normalized.length() <= 50 ? normalized : normalized.substring(0, 50);
    }

    private static PayoutStatus mapStatus(String value) {
        if (value == null) return PayoutStatus.UNKNOWN;
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "success", "successful", "completed" -> PayoutStatus.COMPLETED;
            case "pending", "processing", "otp" -> PayoutStatus.PENDING;
            case "failed", "reversed" -> PayoutStatus.FAILED;
            case "cancelled", "canceled" -> PayoutStatus.CANCELLED;
            default -> PayoutStatus.UNKNOWN;
        };
    }
}
