package com.rackpay.api.persistence.remittance;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "remittance_countries")
public class RemittanceCountryEntity {
    @Id
    @Column(length = 2)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "dial_code", nullable = false, length = 8)
    private String dialCode;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "send_enabled", nullable = false)
    private boolean sendEnabled;

    @Column(name = "receive_enabled", nullable = false)
    private boolean receiveEnabled;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected RemittanceCountryEntity() {}

    public String getCode() { return code; }
    public String getName() { return name; }
    public String getDialCode() { return dialCode; }
    public String getCurrencyCode() { return currencyCode; }
    public boolean isEnabled() { return enabled; }
    public boolean isSendEnabled() { return sendEnabled; }
    public boolean isReceiveEnabled() { return receiveEnabled; }
}
