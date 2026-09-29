package com.rackpay.api.remittance.core.model;

import com.rackpay.api.payment.adapters.out.persistence.Type;
import com.rackpay.api.remittance.ports.out.PayoutProvider;

public enum PayoutProviderType {
    FLUTTERWAVE,
    PAYSTACK
}
