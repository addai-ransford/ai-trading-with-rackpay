package com.rackpay.api.remittance;

import java.math.BigDecimal;
import java.util.UUID;

import com.rackpay.api.domain.money.Currency;
import com.rackpay.api.persistence.remittance.RemittanceEntity;

public record PreparedPayout(
        UUID remittanceId,
        UUID attemptId,
        PayoutProviderType provider,
        String providerTransferId,
        String payoutReference,
        Currency sourceCurrency,
        BigDecimal destinationAmount,
        Currency destinationCurrency,
        String countryCode,
        PayoutMethod payoutMethod,
        String networkCode,
        String normalizedPhoneNumber,
        String recipientName,
        RemittanceEntity.Status status,
        boolean executionClaimed,
        UUID executionClaimToken
        ) {

}
