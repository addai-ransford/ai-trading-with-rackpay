package com.rackpay.api.payment.adapters.in.web;

import com.rackpay.api.payment.adapters.out.persistence.Type;
import com.rackpay.api.payment.adapters.out.providers.stripe.Response;
import com.rackpay.api.payment.core.model.PaymentProviderType;
import com.rackpay.api.payment.ports.out.PaymentProvider;

import com.rackpay.api.payment.adapters.out.persistence.PaymentProviderConfigEntity;
import com.rackpay.api.payment.core.service.PaymentProviderService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/api/v1/admin/payment-providers")
public class PaymentProviderAdminController {
    private final PaymentProviderService service;
    public PaymentProviderAdminController(PaymentProviderService service){this.service=service;}
    @GetMapping public List<PaymentProviderResponse> list(){return service.list().stream().map(PaymentProviderResponse::from).toList();}
    @PutMapping("/active") public PaymentProviderResponse activate(Authentication a,@Valid @RequestBody ActivatePaymentProviderRequest r){return PaymentProviderResponse.from(service.activate(r.provider(),a.getName()));}
    @PutMapping("/{id}/enabled") public PaymentProviderResponse setEnabled(Authentication a,@PathVariable UUID id,@Valid @RequestBody SetEnabledRequest r){return PaymentProviderResponse.from(service.setEnabled(id,r.enabled(),a.getName()));}
    public record ActivatePaymentProviderRequest(@NotNull PaymentProviderType provider){}
    public record SetEnabledRequest(@NotNull Boolean enabled){}
    public record PaymentProviderResponse(UUID id,PaymentProviderType provider,boolean enabled,boolean active,String environment,Instant updatedAt){
        static PaymentProviderResponse from(PaymentProviderConfigEntity e){return new PaymentProviderResponse(e.getId(),e.getProvider(),e.isEnabled(),e.isActive(),e.getEnvironment(),e.getUpdatedAt());}
    }
}
