package com.rackpay.api.persistence.payment;

import com.rackpay.api.payment.PaymentProviderType;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="payment_provider_events",uniqueConstraints=@UniqueConstraint(name="uq_payment_provider_event",columnNames={"provider","event_id"}))
public class PaymentProviderEventEntity {
    @Id private UUID id;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private PaymentProviderType provider;
    @Column(name="event_id",nullable=false,length=255) private String eventId;
    @Column(name="provider_payment_id",length=255) private String providerPaymentId;
    @Column(name="event_type",nullable=false,length=100) private String eventType;
    @Column(name="received_at",nullable=false,updatable=false) private Instant receivedAt;
    @Column(name="processed_at") private Instant processedAt;
    @Column(nullable=false,length=20) private String status;
    protected PaymentProviderEventEntity(){}
    public PaymentProviderEventEntity(UUID id,PaymentProviderType provider,String eventId,String paymentId,String eventType,Instant now){
        this.id=id;this.provider=provider;this.eventId=eventId;this.providerPaymentId=paymentId;this.eventType=eventType;this.receivedAt=now;this.status="RECEIVED";
    }
    public UUID getId(){return id;} public PaymentProviderType getProvider(){return provider;} public String getEventId(){return eventId;} public String getProviderPaymentId(){return providerPaymentId;} public String getStatus(){return status;}
    public void markProcessed(Instant now){status="PROCESSED";processedAt=now;}
}