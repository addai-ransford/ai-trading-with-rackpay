package com.rackpay.api.remittance.adapters.in.web;

import com.rackpay.api.remittance.core.service.RemittancePayoutService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/remittances")
public class RemittancePayoutController {
    private final RemittancePayoutService payouts;

    public RemittancePayoutController(RemittancePayoutService payouts) {
        this.payouts = payouts;
    }

    @PostMapping("/{remittanceId}/payout")
    public ResponseEntity<RemittancePayoutService.PayoutResponse> execute(
        @PathVariable UUID remittanceId
    ) {
        return ResponseEntity.ok(payouts.execute(remittanceId));
    }
}
