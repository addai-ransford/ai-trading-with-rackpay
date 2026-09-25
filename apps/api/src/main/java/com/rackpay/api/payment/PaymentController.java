package com.rackpay.api.payment;

import com.rackpay.api.domain.money.Currency;
import com.rackpay.api.service.PaymentService;
import com.rackpay.api.service.PaymentService.AuthenticationData;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;

@RestController
@RequestMapping("/api/v1/wallet/payments")
public class PaymentController {
    private final PaymentService service;
    public PaymentController(PaymentService service){this.service=service;}
    @PostMapping
    public PaymentService.PaymentResponse create(Authentication authentication,@Valid @RequestBody CreatePaymentRequest request){
        return service.create(new AuthenticationData(authentication),request.reference(),request.amount(),request.currency(),request.description(),request.returnUrl(),request.webhookUrl(),request.customerEmail());
    }
    public record CreatePaymentRequest(
        @NotBlank @Size(max=100) String reference,
        @NotNull @DecimalMin(value="0.01") BigDecimal amount,
        @NotNull Currency currency,
        @NotBlank @Size(max=255) String description,
        @NotBlank @Size(max=1000) String returnUrl,
        @NotBlank @Size(max=1000) String webhookUrl,
        @Email @Size(max=320) String customerEmail
    ){}
}
