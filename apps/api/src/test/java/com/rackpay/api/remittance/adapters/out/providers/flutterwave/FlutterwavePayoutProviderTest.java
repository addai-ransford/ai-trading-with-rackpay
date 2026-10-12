package com.rackpay.api.remittance.adapters.out.providers.flutterwave;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.rackpay.api.remittance.core.model.PayoutMethod;
import com.rackpay.api.remittance.ports.out.PayoutProvider.CreatePayoutCommand;
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
                mock(ProviderRetryExecutor.class),
                mock(FlutterwaveTransferGateway.class)
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
                mock(ProviderRetryExecutor.class),
                mock(FlutterwaveTransferGateway.class)
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

    @Test
    void treatsSuccessfulResponseWithoutTransferIdAsUncertain() {
        FlutterwaveTransferGateway gateway = mock(FlutterwaveTransferGateway.class);
        when(gateway.createTransfer(any())).thenReturn(
                new FlutterwaveTransferGateway.TransferResponse(null, "success", "NEW", "Transfer initiated")
        );
        FlutterwavePayoutProvider provider = new FlutterwavePayoutProvider(
                RestClient.builder(),
                "http://flutterwave.test/v3",
                "test-secret",
                mock(ProviderRetryExecutor.class),
                gateway
        );

        assertThatThrownBy(() -> provider.createPayout(validCommand()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("reconcile by reference before retrying");
    }

    @Test
    void treatsTransferIdWithUnsuccessfulResponseAsUncertain() {
        FlutterwaveTransferGateway gateway = mock(FlutterwaveTransferGateway.class);
        when(gateway.createTransfer(any())).thenReturn(
                new FlutterwaveTransferGateway.TransferResponse("12345", "error", null, "Unexpected response")
        );
        FlutterwavePayoutProvider provider = new FlutterwavePayoutProvider(
                RestClient.builder(),
                "http://flutterwave.test/v3",
                "test-secret",
                mock(ProviderRetryExecutor.class),
                gateway
        );

        assertThatThrownBy(() -> provider.createPayout(validCommand()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("reconcile by reference before retrying");
    }

    @Test
    void createsPayoutThroughSdkGatewayWithNormalizedTransferDetails() {
        RestClient.Builder builder = RestClient.builder();
        FlutterwaveTransferGateway gateway = mock(FlutterwaveTransferGateway.class);
        when(gateway.createTransfer(any())).thenReturn(
                new FlutterwaveTransferGateway.TransferResponse("12345", "success", "NEW", "Transfer initiated")
        );
        FlutterwavePayoutProvider provider = new FlutterwavePayoutProvider(
                builder,
                "http://flutterwave.test/v3",
                "test-secret",
                mock(ProviderRetryExecutor.class),
                gateway
        );

        var result = provider.createPayout(validCommand());

        assertThat(result.providerTransferId()).isEqualTo("12345");
        assertThat(result.status()).isEqualTo("NEW");

        ArgumentCaptor<FlutterwaveTransferGateway.TransferCommand> captor =
                ArgumentCaptor.forClass(FlutterwaveTransferGateway.TransferCommand.class);
        verify(gateway).createTransfer(captor.capture());
        assertThat(captor.getValue().reference()).isEqualTo("rackpay-remit-001");
        assertThat(captor.getValue().amount()).isEqualByComparingTo("25");
        assertThat(captor.getValue().currency()).isEqualTo("GHS");
        assertThat(captor.getValue().debitCurrency()).isEqualTo("GHS");
        assertThat(captor.getValue().bankCode()).isEqualTo("MTN");
        assertThat(captor.getValue().accountNumber()).isEqualTo("233241234567");
    }

    private CreatePayoutCommand validCommand() {
        return new CreatePayoutCommand(
                "rackpay-remit-001",
                new BigDecimal("25"),
                Currency.GHS,
                Currency.GHS,
                "GH",
                PayoutMethod.MOBILE_MONEY,
                "MTN",
                "+233241234567",
                "Recipient Name"
        );
    }

}
