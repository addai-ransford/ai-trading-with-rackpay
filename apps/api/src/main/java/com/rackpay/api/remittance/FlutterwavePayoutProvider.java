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
            "account_bank", command.networkCode(),
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
        throw new UnsupportedOperationException("Flutterwave payout execution is enabled in the next remittance execution step");
    }

    @Override
    public PayoutStatus getPayout(String providerTransferId) {
        throw new UnsupportedOperationException("Flutterwave payout status is enabled in the next remittance execution step");
    }
}
