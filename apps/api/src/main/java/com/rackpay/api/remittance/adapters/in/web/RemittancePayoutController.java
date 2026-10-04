package com.rackpay.api.remittance.adapters.in.web;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.rackpay.api.remittance.core.service.RemittancePayoutService;

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
