package com.rackpay.api.remittance.adapters.in.web;

import com.rackpay.api.payment.adapters.out.providers.stripe.Response;
import com.rackpay.api.remittance.core.service.RemittanceHistoryService;
import com.rackpay.api.remittance.core.service.RemittanceResponse;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/remittances")
public class RemittanceHistoryController {
    private final RemittanceHistoryService history;

    public RemittanceHistoryController(RemittanceHistoryService history) {
        this.history = history;
    }

    @GetMapping
    public List<RemittanceHistoryService.RemittanceResponse> list(
        Authentication authentication
    ) {
        return history.list(authentication);
    }
}
