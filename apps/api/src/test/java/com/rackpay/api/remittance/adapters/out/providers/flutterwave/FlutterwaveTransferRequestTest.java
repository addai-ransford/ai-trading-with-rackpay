package com.rackpay.api.remittance.adapters.out.providers.flutterwave;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

class FlutterwaveTransferRequestTest {

    @Test
    void serializesBeneficiaryNameRequiredForMobileMoneyTransfers() throws Exception {
        var request = new FlutterwaveJavaV3TransferGateway.BeneficiaryTransferRequest(
            "https://rackpay.example/webhooks/flutterwave",
            "rackpay-remit-001",
            "EUR",
            "MTN",
            "233241234567",
            "RackPay remittance rackpay-remit-001",
            new BigDecimal("1200"),
            "GHS",
            "Ama Mensah"
        );

        var json = new ObjectMapper().readTree(request.toString());

        assertThat(json.path("beneficiary_name").asText()).isEqualTo("Ama Mensah");
        assertThat(json.path("account_bank").asText()).isEqualTo("MTN");
        assertThat(json.path("account_number").asText()).isEqualTo("233241234567");
        assertThat(json.path("currency").asText()).isEqualTo("GHS");
    }

    @Test
    void serializesKenyaSenderMetadataForMpesaTransfers() throws Exception {
        var request = new FlutterwaveJavaV3TransferGateway.BeneficiaryTransferRequest(
            null, "rackpay-ke-001", "EUR", "MPS", "2540782773934",
            "RackPay remittance rackpay-ke-001", new BigDecimal("1200"), "KES",
            "Akinyi Kimwei", "Flutterwave Developers", "ZA", "23457558595"
        );

        var json = new ObjectMapper().readTree(request.toString());

        assertThat(json.path("account_bank").asText()).isEqualTo("MPS");
        assertThat(json.path("account_number").asText()).isEqualTo("2540782773934");
        assertThat(json.path("beneficiary_name").asText()).isEqualTo("Akinyi Kimwei");
        assertThat(json.path("meta").path("sender").asText()).isEqualTo("Flutterwave Developers");
        assertThat(json.path("meta").path("sender_country").asText()).isEqualTo("ZA");
        assertThat(json.path("meta").path("mobile_number").asText()).isEqualTo("23457558595");
    }
}
