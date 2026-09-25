package com.rackpay.api.payment;

import com.rackpay.api.domain.money.Currency;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import java.math.BigDecimal;
import java.util.Map;

@Component
public class StripePaymentProvider implements PaymentProvider {
    private final RestClient client; private final String secretKey;

    public StripePaymentProvider(RestClient.Builder builder,
        @Value("https://api.stripe.com/v1") String baseUrl,
        @Value("$"+"{RACKPAY_STRIPE_SECRET_KEY:}") String secretKey) {
        this.secretKey=secretKey;this.client=builder.baseUrl(baseUrl).build();
    }

    public PaymentProviderType type(){return PaymentProviderType.STRIPE;}

    public PaymentSession createPayment(CreatePaymentCommand c){
        requireConfigured();
        MultiValueMap<String,String> form=new LinkedMultiValueMap<>();
        form.add("mode","payment"); form.add("success_url",c.returnUrl()); form.add("cancel_url",c.returnUrl());
        form.add("line_items[0][quantity]","1");
        form.add("line_items[0][price_data][currency]",c.currency().name().toLowerCase());
        form.add("line_items[0][price_data][unit_amount]",c.amount().movePointRight(2).toBigInteger().toString());
        form.add("line_items[0][price_data][product_data][name]",c.description());
        form.add("metadata[reference]",c.reference());
        Response r=client.post().uri("/checkout/sessions").headers(h->h.setBasicAuth(secretKey,""))
            .contentType(org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED).body(form).retrieve().body(Response.class);
        if(r==null||r.id()==null||r.url()==null)throw new IllegalStateException("Stripe returned an invalid checkout response");
        return new PaymentSession(r.id(),r.url());
    }

    public PaymentStatus getPayment(String id){requireConfigured();return PaymentStatus.UNKNOWN;}
    public void cancelPayment(String id){throw new UnsupportedOperationException("Stripe Checkout cancellation requires session-specific integration");}
    public void refundPayment(String id,BigDecimal amount,Currency currency){throw new UnsupportedOperationException("Stripe refunds require payment transaction integration");}
    public WebhookResult handleWebhook(String payload,Map<String,String> headers){throw new UnsupportedOperationException("Stripe webhook verification is not enabled yet");}
    private void requireConfigured(){if(secretKey==null||secretKey.isBlank())throw new IllegalStateException("Stripe secret key is not configured");}
    private record Response(String id,String url){}
}
