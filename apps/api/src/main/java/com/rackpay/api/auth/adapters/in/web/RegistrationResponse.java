package com.rackpay.api.auth.adapters.in.web;

import java.util.UUID;

public record RegistrationResponse(
    UUID userId,
    UUID walletId,
    String currency
) {}
