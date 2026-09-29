package com.rackpay.api.payment.core.model;

import com.rackpay.api.payment.adapters.out.persistence.Type;
import com.rackpay.api.payment.ports.out.PaymentProvider;

public enum PaymentProviderType {
    MOLLIE,
    STRIPE,
    ADYEN,
    PAYPAL
}
