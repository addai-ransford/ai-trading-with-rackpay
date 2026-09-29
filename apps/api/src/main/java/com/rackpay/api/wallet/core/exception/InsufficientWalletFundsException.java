package com.rackpay.api.wallet.core.exception;

import com.rackpay.api.wallet.core.model.Wallet;

public class InsufficientWalletFundsException extends IllegalStateException {
    public InsufficientWalletFundsException() {
        super("insufficient wallet funds");
    }
}
