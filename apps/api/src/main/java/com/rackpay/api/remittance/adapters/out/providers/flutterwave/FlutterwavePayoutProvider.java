package com.rackpay.api.remittance.adapters.out.providers.flutterwave;

import java.math.RoundingMode;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.fasterxml.jackson.databind.JsonNode;
import com.rackpay.api.remittance.core.model.PayoutMethod;
import com.rackpay.api.remittance.core.model.PayoutProviderType;
import com.rackpay.api.remittance.ports.out.PayoutProvider;
import com.rackpay.api.shared.core.money.Currency;
import com.rackpay.api.shared.infrastructure.retry.ProviderRetryClassifier;
import com.rackpay.api.shared.infrastructure.retry.ProviderRetryExecutor;

@Component
public class FlutterwavePayoutProvider implements PayoutProvider {

    private final RestClient client;
    private final String secretKey;
    private final ProviderRetryExecutor retryExecutor;
    private static final Logger log = LoggerFactory.getLogger(FlutterwavePayoutProvider.class);

    public FlutterwavePayoutProvider(
            RestClient.Builder builder,
            @Value("${rackpay.payout.flutterwave.base-url:https://api.flutterwave.com/v3}") String baseUrl,
            @Value("${rackpay.payout.flutterwave.secret-key:}") String secretKey,
            ProviderRetryExecutor retryExecutor
    ) {
        this.client = builder.baseUrl(baseUrl).build();
        this.secretKey = secretKey;
        this.retryExecutor = retryExecutor;
    }

    @Override
    public PayoutProviderType type() {
        return PayoutProviderType.FLUTTERWAVE;
    }

    @Override
    public boolean supportsPayout(CreatePayoutCommand command) {
        return !secretKey.isBlank()
                && command.payoutMethod() == PayoutMethod.MOBILE_MONEY
                && command.currency() != null
                && supportedNetwork(command.countryCode(), command.currency(), command.networkCode());
    }

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
                "country", command.countryCode(),
                "currency", command.currency().name()
        );

        JsonNode response;
        try {
            response = client.post()
                    .uri("/accounts/resolve")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + secretKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException exception) {
            String providerMessage = providerErrorMessage(exception);
            String failureReason = switch (exception.getStatusCode().value()) {
                case 400, 422 ->
                    providerMessage == null
                            ? "The provider rejected this phone number or mobile-money network."
                            : "Flutterwave: " + providerMessage;
                case 401, 403 ->
                    "Recipient verification provider credentials were rejected.";
                default ->
                    "Recipient verification is temporarily unavailable. Try again later.";
            };
            return new RecipientVerification(
                    false, command.normalizedPhoneNumber(), null, null, failureReason
            );
        } catch (RestClientException exception) {
            return new RecipientVerification(
                    false,
                    command.normalizedPhoneNumber(),
                    null,
                    null,
                    "Could not reach the recipient verification service. Try again later."
            );
        }

        if (response == null) {
            throw new IllegalStateException("Flutterwave returned an empty recipient verification response");
        }

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
        long transferAmount = command.amount().setScale(0, RoundingMode.UNNECESSARY).longValueExact();
        body.put("amount", transferAmount);
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
        return retryExecutor.execute(
                "FLUTTERWAVE",
                "payout-status",
                () -> {
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
                },
                ProviderRetryClassifier::isTransient
        );
    }

    @Override
    public PayoutResult findPayoutByReference(String reference) {
        return retryExecutor.execute(
                "FLUTTERWAVE",
                "payout-reconciliation",
                () -> {
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
                },
                ProviderRetryClassifier::isTransient
        );
    }

    private String providerErrorMessage(RestClientResponseException exception) {
        String responseBody = exception.getResponseBodyAsString();
        if (responseBody == null || responseBody.isBlank()) {
            return null;
        }

        try {
            JsonNode payload = new com.fasterxml.jackson.databind.ObjectMapper().readTree(responseBody);
            String message = payload.path("message").asText(null);
            return message == null || message.isBlank() ? null : message.trim();
        } catch (Exception ignored) {
            return null;
        }
    }

    private boolean supportedNetwork(String countryCode, Currency currency, String networkCode) {
        if (countryCode == null || currency == null || networkCode == null) {
            return false;
        }
        String country = countryCode.toUpperCase(java.util.Locale.ROOT);
        String network = networkCode.toUpperCase(java.util.Locale.ROOT);
        return switch (country) {
            case "GH" ->
                currency == Currency.GHS
                && switch (network) {
                    case "MTN", "AIRTELTIGO", "TELECEL" ->
                        true;
                    default ->
                        false;
                };
            case "KE" ->
                currency == Currency.KES && "MPESA".equals(network);
            case "CI" ->
                currency == Currency.XOF
                && switch (network) {
                    case "ORANGE", "WAVE" ->
                        true;
                    default ->
                        false;
                };
            default ->
                false;
        };
    }

    private String providerNetworkCode(String networkCode) {
        return switch (networkCode.toUpperCase(java.util.Locale.ROOT)) {
            case "MPESA" ->
                "MPS";
            case "TELECEL" ->
                "VODAFONE";
            default ->
                networkCode;
        };
    }

    private PayoutStatus mapStatus(String status) {
        if (status == null) {
            return PayoutStatus.UNKNOWN;
        }
        return switch (status.toUpperCase(java.util.Locale.ROOT)) {
            case "NEW", "INITIATED" ->
                PayoutStatus.CREATED;
            case "PENDING", "PROCESSING" ->
                PayoutStatus.PROCESSING;
            case "SUCCESSFUL", "SUCCESS", "COMPLETED" ->
                PayoutStatus.COMPLETED;
            case "FAILED", "ERROR" ->
                PayoutStatus.FAILED;
            case "CANCELLED", "CANCELED" ->
                PayoutStatus.CANCELLED;
            default ->
                PayoutStatus.UNKNOWN;
        };
    }
}
