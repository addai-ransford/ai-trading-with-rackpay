package com.rackpay.api.remittance;

import com.fasterxml.jackson.databind.JsonNode;
import com.rackpay.api.domain.money.Currency;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import java.util.Map;

@Component
public class FlutterwavePayoutProvider implements PayoutProvider {
    private final RestClient client;
    private final String secretKey;

    public FlutterwavePayoutProvider(
        RestClient.Builder builder,
        @Value("\${rackpay.payout.flutterwave.base-url:https://api.flutterwave.com/v3}") String baseUrl,
        @Value("\${rackpay.payout.flutterwave.secret-key:}") String secretKey
    ) {
        this.client = builder.baseUrl(baseUrl).build();
        this.secretKey = secretKey;
    }

    @Override
    public PayoutProviderType type() { return PayoutProviderType.FLUTTERWAVE; }

    @Override
    public boolean supportsRecipientVerification(String countryCode, Currency currency, PayoutMethod payoutMethod) {
        return "GH".equalsIgnoreCase(countryCode)
            && currency == Currency.GHS
            && payoutMethod == PayoutMethod.MOBILE_MONEY
            && !secretKey.isBlank();
    }

    @Override
    public RecipientVerification verifyRecipient(VerifyRecipientCommand command) {
        if (!supportsRecipientVerification(command.countryCode(), command.currency(), command.payoutMethod())) {
            throw new IllegalArgumentException("Flutterwave recipient verification is not supported for this destination");
        }

        String accountNumber = command.normalizedPhoneNumber().replace("+", "");
        Map<String, String> body = Map.of(
            "account_number", accountNumber,
            "account_bank", providerNetworkCode(command.networkCode()),
            "country", command.countryCode()
        );

        JsonNode response = client.post()
            .uri("/accounts/resolve")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + secretKey)
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve()
            .body(JsonNode.class);

        if (response == null) throw new IllegalStateException("Flutterwave returned an empty recipient verification response");

        if (!"success".equalsIgnoreCase(response.path("status").asText())) {
            return new RecipientVerification(
                false, command.normalizedPhoneNumber(), null, null,
                response.path("message").asText("Recipient could not be verified")
            );
        }

        JsonNode data = response.path("data");
        String name = data.path("account_name").asText(null);
        if (name == null || name.isBlank()) {
            return new RecipientVerification(false, command.normalizedPhoneNumber(), null, null, "Provider did not return a recipient name");
        }

        return new RecipientVerification(
            true,
            command.normalizedPhoneNumber(),
            name.trim(),
            data.path("account_number").asText(null),
            null
        );
    }

    @Override
    public PayoutResult createPayout(CreatePayoutCommand command) {
        if (secretKey.isBlank()) {
            throw new IllegalStateException("Flutterwave secret key is not configured");
        }
        if (command.payoutMethod() != PayoutMethod.MOBILE_MONEY) {
            throw new IllegalArgumentException("Flutterwave currently supports mobile money payouts in RackPay");
        }
        if (command.networkCode() == null || command.networkCode().isBlank()) {
            throw new IllegalArgumentException("mobile money network is required");
        }
        if (command.recipientName() == null || command.recipientName().isBlank()) {
            throw new IllegalArgumentException("verified recipient name is required");
        }

        Map<String, Object> body = new java.util.HashMap<>();
        body.put("account_bank", providerNetworkCode(command.networkCode()));
        body.put("account_number", command.normalizedPhoneNumber().replace("+", ""));
        body.put("amount", command.amount().stripTrailingZeros());
        body.put("currency", command.currency().name());
        body.put("beneficiary_name", command.recipientName());
        body.put("reference", command.reference());
        body.put("narration", "RackPay remittance " + command.reference());
        if (command.sourceCurrency() != null && command.sourceCurrency() != command.currency()) {
            body.put("debit_currency", command.sourceCurrency().name());
        }

        JsonNode response = client.post()
            .uri("/transfers")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + secretKey)
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve()
            .body(JsonNode.class);

        if (response == null) {
            throw new IllegalStateException("Flutterwave returned an empty payout response");
        }

        JsonNode data = response.path("data");
        String providerId = data.path("id").isNumber()
            ? data.path("id").asText()
            : data.path("id").asText(null);
        String status = data.path("status").asText(null);

        if (!"success".equalsIgnoreCase(response.path("status").asText())
            || providerId == null || providerId.isBlank()) {
            String message = response.path("message").asText("Flutterwave payout creation failed");
            return new PayoutResult(providerId, "FAILED:" + message);
        }

        return new PayoutResult(providerId, status == null ? "NEW" : status);
    }

    @Override
    public PayoutStatus getPayout(String providerTransferId) {
        JsonNode response = client.get()
            .uri("/transfers/{id}", providerTransferId)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + secretKey)
            .accept(MediaType.APPLICATION_JSON)
            .retrieve()
            .body(JsonNode.class);

        if (response == null) {
            return PayoutStatus.UNKNOWN;
        }

        JsonNode data = response.path("data");
        return mapStatus(data.path("status").asText(null));
    }

    @Override
    public PayoutResult findPayoutByReference(String reference) {
        JsonNode response = client.get()
            .uri(uriBuilder -> uriBuilder.path("/transfers")
                .queryParam("reference", reference)
                .queryParam("page_size", 10)
                .build())
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + secretKey)
            .accept(MediaType.APPLICATION_JSON)
            .retrieve()
            .body(JsonNode.class);

        if (response == null) {
            return null;
        }

        JsonNode data = response.path("data");
        if (!data.isArray() || data.isEmpty()) {
            return null;
        }

        JsonNode transfer = data.get(0);
        String id = transfer.path("id").asText(null);
        String status = transfer.path("status").asText(null);
        return id == null ? null : new PayoutResult(id, status);
    }

    private String providerNetworkCode(String networkCode) {
        return switch (networkCode.toUpperCase(java.util.Locale.ROOT)) {
            case "MPESA" -> "MPS";
            case "TELECEL" -> "VODAFONE";
            default -> networkCode;
        };
    }

    private PayoutStatus mapStatus(String status) {
        if (status == null) return PayoutStatus.UNKNOWN;
        return switch (status.toUpperCase(java.util.Locale.ROOT)) {
            case "NEW", "INITIATED" -> PayoutStatus.CREATED;
            case "PENDING", "PROCESSING" -> PayoutStatus.PROCESSING;
            case "SUCCESSFUL", "SUCCESS", "COMPLETED" -> PayoutStatus.COMPLETED;
            case "FAILED", "ERROR" -> PayoutStatus.FAILED;
            case "CANCELLED", "CANCELED" -> PayoutStatus.CANCELLED;
            default -> PayoutStatus.UNKNOWN;
        };
    }
}
