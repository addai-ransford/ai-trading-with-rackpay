package com.rackpay.api.remittance;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/remittances/recipients")
public class RemittanceRecipientController {
    private final RemittanceRecipientVerificationService service;

    public RemittanceRecipientController(RemittanceRecipientVerificationService service) {
        this.service = service;
    }

    @PostMapping("/verify")
    public RemittanceRecipientVerificationService.VerificationResponse verify(
        Authentication authentication,
        @RequestBody RemittanceRecipientVerificationService.VerificationRequest request
    ) {
        return service.verify(authentication, request);
    }
}
