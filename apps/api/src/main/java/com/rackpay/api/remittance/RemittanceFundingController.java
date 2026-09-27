package com.rackpay.api.remittance;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/remittances")
public class RemittanceFundingController {
    private final RemittanceFundingService funding;

    public RemittanceFundingController(RemittanceFundingService funding) {
        this.funding = funding;
    }

    @PostMapping("/fund")
    public ResponseEntity<RemittanceFundingService.FundingResponse> fund(
        Authentication authentication,
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @Valid @RequestBody FundingRequest request
    ) {
        return ResponseEntity.ok(
            funding.fund(authentication, request.quoteId(), idempotencyKey)
        );
    }

    public record FundingRequest(@NotNull UUID quoteId) {}
}
