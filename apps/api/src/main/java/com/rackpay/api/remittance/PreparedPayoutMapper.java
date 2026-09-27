package com.rackpay.api.remittance;

import com.rackpay.api.persistence.remittance.RemittanceEntity;

public class PreparedPayoutMapper {
    public PreparedPayout toPreparedPayout(
        RemittanceEntity remittance,
        PayoutProviderType provider,
        RemittancePayoutService.PayoutResponse response
    ) {
        return new PreparedPayout(
            remittance.getId(),
            null,
            provider,
            response.providerTransferId(),
            remittance.getPayoutReference(),
            remittance.getSourceCurrency(),
            remittance.getDestinationAmount(),
            remittance.getDestinationCurrency(),
            null,
            null,
            null,
            null,
            null,
            response.status()
        );
    }
}
