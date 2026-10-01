package com.rackpay.api.remittance.core.model;

import com.rackpay.api.remittance.ports.out.PayoutProvider;

import java.math.BigDecimal;
import java.util.UUID;

import com.rackpay.api.shared.core.money.Currency;
import com.rackpay.api.remittance.adapters.out.persistence.RemittanceEntity;

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
