package com.rackpay.api.remittance.adapters.out.persistence;

import com.rackpay.api.remittance.core.model.MobileMoneyNetwork;
import com.rackpay.api.shared.core.money.Money;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "mobile_money_networks")
public class MobileMoneyNetworkEntity {
    @Id
    private UUID id;

    @Column(name = "country_code", nullable = false, length = 2)
    private String countryCode;

    @Column(nullable = false, length = 30)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "flutterwave_code", length = 50)
    private String flutterwaveCode;

    @Column(name = "paystack_code", length = 50)
    private String paystackCode;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected MobileMoneyNetworkEntity() {}

    public MobileMoneyNetworkEntity(UUID id, String countryCode, String code, String name,
        String flutterwaveCode, String paystackCode) {
        this.id = id;
        this.countryCode = countryCode;
        this.code = code;
        this.name = name;
        this.flutterwaveCode = flutterwaveCode;
        this.paystackCode = paystackCode;
        this.enabled = true;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void update(String name, String flutterwaveCode, String paystackCode, boolean enabled) {
        this.name = name;
        this.flutterwaveCode = flutterwaveCode;
        this.paystackCode = paystackCode;
        this.enabled = enabled;
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getCountryCode() { return countryCode; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getFlutterwaveCode() { return flutterwaveCode; }
    public String getPaystackCode() { return paystackCode; }
    public boolean isEnabled() { return enabled; }
}
