package com.rackpay.api.payment.adapters.in.web;

import com.rackpay.api.payment.core.model.PaymentProviderType;
import com.rackpay.api.payment.ports.out.PaymentProvider;

import com.rackpay.api.payment.core.service.PaymentWebhookService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/payments/webhooks")
public class PaymentWebhookController {
    private final PaymentWebhookService service;
    public PaymentWebhookController(PaymentWebhookService service){this.service=service;}

    @PostMapping("/{provider}")
    public ResponseEntity<Void> handle(@PathVariable PaymentProviderType provider,
                                       @RequestBody String payload,
                                       @RequestHeader Map<String,String> headers){
        service.handle(provider,payload,headers);
        return ResponseEntity.ok().build();
    }
}
