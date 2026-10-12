package com.rackpay.api.remittance.adapters.out.providers.flutterwave;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.rackpay.api.remittance.core.model.PayoutMethod;
import com.rackpay.api.remittance.ports.out.PayoutProvider.VerifyRecipientCommand;
import com.rackpay.api.remittance.ports.out.PayoutProvider.RecipientVerification;
import com.rackpay.api.shared.core.money.Currency;
import com.rackpay.api.shared.infrastructure.retry.ProviderRetryExecutor;

class FlutterwavePayoutProviderTest {

    @Test
    void preservesFlutterwaveMessageWhenRecipientVerificationIsRejected() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        FlutterwavePayoutProvider provider = new FlutterwavePayoutProvider(
                builder,
                "http://flutterwave.test/v3",
                "test-secret",
                mock(ProviderRetryExecutor.class)
        );

        server.expect(requestTo("http://flutterwave.test/v3/accounts/resolve"))
                .andRespond(withStatus(UNPROCESSABLE_ENTITY)
                        .contentType(APPLICATION_JSON)
                        .body("""
                                {
                                  "status": "error",
                                  "message": "The account number is not valid for the selected bank"
                                }
                                """));

        RecipientVerification result = provider.verifyRecipient(
                new VerifyRecipientCommand(
                        "GH",
                        Currency.GHS,
                        PayoutMethod.MOBILE_MONEY,
                        "MTN",
                        "+233241234567"
                )
        );

        assertThat(result.verified()).isFalse();
        assertThat(result.failureReason())
                .isEqualTo("Flutterwave: The account number is not valid for the selected bank");

        server.verify();
    }

    @Test
    void fallsBackToSafeMessageWhenFlutterwaveReturnsNoErrorMessage() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        FlutterwavePayoutProvider provider = new FlutterwavePayoutProvider(
                builder,
                "http://flutterwave.test/v3",
                "test-secret",
                mock(ProviderRetryExecutor.class)
        );

        server.expect(requestTo("http://flutterwave.test/v3/accounts/resolve"))
                .andRespond(withStatus(UNPROCESSABLE_ENTITY)
                        .contentType(APPLICATION_JSON)
                        .body("""
                                {
                                  "status": "error"
                                }
                                """));

        RecipientVerification result = provider.verifyRecipient(
                new VerifyRecipientCommand(
                        "GH",
                        Currency.GHS,
                        PayoutMethod.MOBILE_MONEY,
                        "MTN",
                        "+233241234567"
                )
        );

        assertThat(result.failureReason())
                .isEqualTo("The provider rejected this phone number or mobile-money network.");

        server.verify();
    }
}
