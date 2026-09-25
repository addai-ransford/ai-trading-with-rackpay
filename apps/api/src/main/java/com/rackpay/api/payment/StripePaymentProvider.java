package com.rackpay.api.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rackpay.api.domain.money.Currency;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

@Component
public class StripePaymentProvider implements PaymentProvider {
    private final RestClient client; private final String secretKey; private final String webhookSecret; private final ObjectMapper mapper;
    public StripePaymentProvider(RestClient.Builder builder,@Value("${rackpay.payment.stripe.base-url:https://api.stripe.com/v1}") String baseUrl,
        @Value("$"+"{RACKPAY_STRIPE_SECRET_KEY:}") String secretKey,@Value("$"+"{RACKPAY_STRIPE_WEBHOOK_SECRET:}") String webhookSecret,ObjectMapper mapper){
        this.secretKey=secretKey;this.webhookSecret=webhookSecret;this.mapper=mapper;this.client=builder.baseUrl(baseUrl).build();
    }
    public PaymentProviderType type(){return PaymentProviderType.STRIPE;}
    public PaymentSession createPayment(CreatePaymentCommand c){
        requireConfigured();MultiValueMap<String,String> form=new LinkedMultiValueMap<>();
        form.add("mode","payment");form.add("success_url",c.returnUrl());form.add("cancel_url",c.returnUrl());
        form.add("line_items[0][quantity]","1");form.add("line_items[0][price_data][currency]",c.currency().name().toLowerCase());
        form.add("line_items[0][price_data][unit_amount]",c.amount().movePointRight(2).toBigInteger().toString());
        form.add("line_items[0][price_data][product_data][name]",c.description());form.add("metadata[reference]",c.reference());
        Response r=client.post().uri("/checkout/sessions").headers(h->h.setBasicAuth(secretKey,"")).contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form).retrieve().body(Response.class);
        if(r==null||r.id()==null||r.url()==null)throw new IllegalStateException("Stripe returned an invalid checkout response");
        return new PaymentSession(r.id(),r.url());
    }
    public PaymentStatus getPayment(String id){requireConfigured();Response r=client.get().uri("/checkout/sessions/{id}",id).headers(h->h.setBasicAuth(secretKey,"")).retrieve().body(Response.class);return r==null?PaymentStatus.UNKNOWN:map(r.paymentStatus());}
    public void cancelPayment(String id){throw new UnsupportedOperationException("Stripe Checkout cancellation requires session-specific integration");}
    public void refundPayment(String id,BigDecimal amount,Currency currency){throw new UnsupportedOperationException("Stripe refunds require payment transaction integration");}
    public WebhookResult handleWebhook(String payload,Map<String,String> headers){
        requireConfigured();verifySignature(payload,headers.get("Stripe-Signature"));
        try{
            JsonNode root=mapper.readTree(payload), object=root.path("data").path("object");
            String type=root.path("type").asText(), eventId=root.path("id").asText(), id=object.path("id").asText();
            String status=object.path("payment_status").asText();
            if(id.isBlank())throw new IllegalArgumentException("Stripe webhook has no checkout session id");
            String reference=object.path("metadata").path("reference").asText(null);
            return new WebhookResult(eventId,id,map(type,status),type,reference,object.path("amount_total").isNumber()?object.path("amount_total").decimalValue().movePointLeft(2):null,object.path("currency").asText(null)==null?null:Currency.valueOf(object.path("currency").asText().toUpperCase()));
        }catch(Exception e){throw new IllegalArgumentException("Invalid Stripe webhook payload",e);}
    }
    private PaymentStatus map(String status){return switch(status==null?"":status){case "paid"->PaymentStatus.PAID;case "unpaid"->PaymentStatus.PENDING;default->PaymentStatus.UNKNOWN;};}
    private PaymentStatus map(String type,String status){if("checkout.session.completed".equals(type)||"checkout.session.async_payment_succeeded".equals(type))return PaymentStatus.PAID;if("checkout.session.async_payment_failed".equals(type))return PaymentStatus.FAILED;return map(status);}
    private void requireConfigured(){if(secretKey==null||secretKey.isBlank())throw new IllegalStateException("Stripe secret key is not configured");}
    private void verifySignature(String payload,String signature){
        if(webhookSecret==null||webhookSecret.isBlank())throw new IllegalStateException("Stripe webhook secret is not configured");
        if(signature==null)throw new SecurityException("Missing Stripe webhook signature");
        try{
            String[] parts=signature.split(",");String timestamp=null;List<String> values=new ArrayList<>();
            for(String p:parts){String[] kv=p.split("=",2);if(kv.length!=2)continue;if("t".equals(kv[0]))timestamp=kv[1];if("v1".equals(kv[0]))values.add(kv[1]);}
            if(timestamp==null||values.isEmpty())throw new SecurityException("Invalid Stripe webhook signature");
            long ts=Long.parseLong(timestamp);if(Math.abs(System.currentTimeMillis()/1000-ts)>300)throw new SecurityException("Expired Stripe webhook signature");
            Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
            String signed=timestamp+"."+payload;String expected=HexFormat.of().formatHex(mac.doFinal(signed.getBytes(StandardCharsets.UTF_8)));
            if(values.stream().noneMatch(v->MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),v.getBytes(StandardCharsets.UTF_8))))throw new SecurityException("Invalid Stripe webhook signature");
        }catch(SecurityException e){throw e;}catch(Exception e){throw new SecurityException("Unable to verify Stripe webhook signature",e);}
    }
    private record Response(String id,String url,String paymentStatus){}
}
