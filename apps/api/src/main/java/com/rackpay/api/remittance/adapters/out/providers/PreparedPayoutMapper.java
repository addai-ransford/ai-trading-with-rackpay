package com.rackpay.api.remittance.adapters.out.providers;

import com.rackpay.api.remittance.core.model.PayoutProviderType;
import com.rackpay.api.remittance.core.model.PreparedPayout;
import com.rackpay.api.remittance.core.service.RemittancePayoutService;
import com.rackpay.api.remittance.ports.out.PayoutProvider;
import com.rackpay.api.shared.core.money.Currency;

import com.rackpay.api.remittance.adapters.out.persistence.RemittanceEntity;

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
            response.status(),
            false,
            null
        );
    }
}
