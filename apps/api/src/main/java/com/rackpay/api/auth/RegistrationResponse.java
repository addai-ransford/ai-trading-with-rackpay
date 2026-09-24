package com.rackpay.api.auth;

import java.util.UUID;

public record RegistrationResponse(
    UUID userId,
    UUID walletId,
    String currency
) {}
