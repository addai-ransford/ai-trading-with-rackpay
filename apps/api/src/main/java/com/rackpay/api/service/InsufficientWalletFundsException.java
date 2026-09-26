package com.rackpay.api.service;

public class InsufficientWalletFundsException extends IllegalStateException {
    public InsufficientWalletFundsException() {
        super("insufficient wallet funds");
    }
}
