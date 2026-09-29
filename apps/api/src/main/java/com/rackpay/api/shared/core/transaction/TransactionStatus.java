package com.rackpay.api.shared.core.transaction;

import com.rackpay.api.payment.adapters.out.persistence.Status;

public enum TransactionStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED,
    CANCELLED
}
