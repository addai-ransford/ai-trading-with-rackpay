package com.rackpay.api.remittance.adapters.in.web;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.rackpay.api.remittance.core.service.RemittanceRecipientVerificationService;

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
