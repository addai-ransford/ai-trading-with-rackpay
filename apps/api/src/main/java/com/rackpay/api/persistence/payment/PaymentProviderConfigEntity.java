package com.rackpay.api.persistence.payment;

import com.rackpay.api.payment.PaymentProviderType;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="payment_provider_configs", uniqueConstraints=@UniqueConstraint(name="uq_payment_provider_config_provider", columnNames="provider"))
public class PaymentProviderConfigEntity {
    @Id private UUID id;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private PaymentProviderType provider;
    @Column(nullable=false) private boolean enabled;
    @Column(nullable=false) private boolean active;
    @Column(nullable=false,length=20) private String environment;
    @Column(name="updated_by",length=255) private String updatedBy;
    @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt;
    @Column(name="updated_at",nullable=false) private Instant updatedAt;
    protected PaymentProviderConfigEntity() {}
    public PaymentProviderConfigEntity(UUID id,PaymentProviderType provider,boolean enabled,boolean active,String environment,Instant now){
        this.id=id;this.provider=provider;this.enabled=enabled;this.active=active;this.environment=environment;this.createdAt=now;this.updatedAt=now;
    }
    public UUID getId(){return id;} public PaymentProviderType getProvider(){return provider;} public boolean isEnabled(){return enabled;}
    public boolean isActive(){return active;} public String getEnvironment(){return environment;} public String getUpdatedBy(){return updatedBy;} public Instant getUpdatedAt(){return updatedAt;}
    public void activate(Instant now,String actor){active=true;enabled=true;updatedAt=now;updatedBy=actor;}
    public void deactivate(Instant now,String actor){active=false;updatedAt=now;updatedBy=actor;}
    public void setEnabled(boolean value,Instant now,String actor){enabled=value;updatedAt=now;updatedBy=actor;}
}
