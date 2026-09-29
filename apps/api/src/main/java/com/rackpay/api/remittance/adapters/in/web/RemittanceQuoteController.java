package com.rackpay.api.remittance.adapters.in.web;

import com.rackpay.api.remittance.core.service.RemittanceQuoteService;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/remittances/quotes")
public class RemittanceQuoteController {
    private final RemittanceQuoteService service;
    public RemittanceQuoteController(RemittanceQuoteService service){this.service=service;}

    @PostMapping
    public RemittanceQuoteService.QuoteResponse create(
        Authentication authentication,
        @RequestBody RemittanceQuoteService.QuoteRequest request
    ){return service.createQuote(authentication,request);}
}
