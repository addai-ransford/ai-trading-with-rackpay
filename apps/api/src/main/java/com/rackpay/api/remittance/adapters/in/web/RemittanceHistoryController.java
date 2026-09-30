package com.rackpay.api.remittance.adapters.in.web;

import com.rackpay.api.remittance.core.service.RemittanceHistoryService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

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

    @GetMapping("/{remittanceId}")
    public RemittanceHistoryService.RemittanceResponse get(
        Authentication authentication,
        @PathVariable UUID remittanceId
    ) {
        return history.get(authentication, remittanceId);
    }
}
