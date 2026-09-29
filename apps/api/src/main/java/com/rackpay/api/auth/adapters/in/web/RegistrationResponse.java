package com.rackpay.api.auth.adapters.in.web;

import com.rackpay.api.payment.adapters.out.providers.stripe.Response;

import java.util.UUID;

public record RegistrationResponse(
    UUID userId,
    UUID walletId,
    String currency
) {}
