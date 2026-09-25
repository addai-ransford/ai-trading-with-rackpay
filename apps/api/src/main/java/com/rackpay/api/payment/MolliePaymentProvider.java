package com.rackpay.api.payment;

import com.rackpay.api.domain.money.Currency;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import java.math.BigDecimal;
import java.util.Map;

@Component
public class MolliePaymentProvider implements PaymentProvider {
    private final RestClient client;
    private final String apiKey;

    public MolliePaymentProvider(RestClient.Builder builder,
        @Value("https://api.mollie.com/v2") String baseUrl,
        @Value("$"+"{RACKPAY_MOLLIE_API_KEY:}") String apiKey) {
        this.apiKey=apiKey; this.client=builder.baseUrl(baseUrl).build();
    }

    public PaymentProviderType type(){return PaymentProviderType.MOLLIE;}

    public PaymentSession createPayment(CreatePaymentCommand c){
        requireConfigured();
        Map<String,Object> body=Map.of(
            "amount",Map.of("currency",c.currency().name(),"value",c.amount().setScale(2).toPlainString()),
            "description",c.description(),"redirectUrl",c.returnUrl(),"webhookUrl",c.webhookUrl(),
            "metadata",Map.of("reference",c.reference()));
        Response r=client.post().uri("/payments").header("Authorization","Bearer "+apiKey).body(body).retrieve().body(Response.class);
        if(r==null||r.id()==null||r.links()==null||r.links().checkout()==null)throw new IllegalStateException("Mollie returned an invalid payment response");
        return new PaymentSession(r.id(),r.links().checkout().href());
    }

    public PaymentStatus getPayment(String id){requireConfigured();Response r=client.get().uri("/payments/{id}",id).header("Authorization","Bearer "+apiKey).retrieve().body(Response.class);return map(r==null?null:r.status());}
    public void cancelPayment(String id){requireConfigured();client.delete().uri("/payments/{id}",id).header("Authorization","Bearer "+apiKey).retrieve().toBodilessEntity();}
    public void refundPayment(String id,BigDecimal amount,Currency currency){throw new UnsupportedOperationException("Mollie refunds require payment transaction integration");}
    public WebhookResult handleWebhook(String payload,Map<String,String> headers){throw new UnsupportedOperationException("Mollie webhook verification is not enabled yet");}
    private void requireConfigured(){if(apiKey==null||apiKey.isBlank())throw new IllegalStateException("Mollie API key is not configured");}
    private PaymentStatus map(String s){if(s==null)return PaymentStatus.UNKNOWN;return switch(s){case "open"->PaymentStatus.CREATED;case "pending"->PaymentStatus.PENDING;case "authorized"->PaymentStatus.REQUIRES_ACTION;case "paid"->PaymentStatus.PAID;case "failed","expired"->PaymentStatus.FAILED;case "canceled"->PaymentStatus.CANCELLED;case "refunded","charged_back"->PaymentStatus.REFUNDED;default->PaymentStatus.UNKNOWN;};}
    private record Response(String id,String status,Links links){}
    private record Links(Checkout checkout){}
    private record Checkout(String href){}
}
