package com.rackpay.api.domain.money;

public enum Currency {
    EUR(2),
    USD(2),
    GBP(2),
    GHS(2),
    KES(2),
    NGN(2),
    XOF(0),
    UGX(0);

    private final int minorUnits;

    Currency(int minorUnits) {
        this.minorUnits = minorUnits;
    }

    public int minorUnits() {
        return minorUnits;
    }
}
