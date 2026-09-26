package com.rackpay.api.persistence.remittance;

import com.rackpay.api.remittance.PayoutMethod;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "remittance_recipients")
public class RemittanceRecipientEntity {
    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "country_code", nullable = false, length = 2)
    private String countryCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "payout_method", nullable = false, length = 30)
    private PayoutMethod payoutMethod;

    @Column(name = "mobile_money_network_id")
    private UUID mobileMoneyNetworkId;

    @Column(name = "phone_number", length = 32)
    private String phoneNumber;

    @Column(name = "normalized_phone_number", length = 32)
    private String normalizedPhoneNumber;

    @Column(name = "verified_name", length = 255)
    private String verifiedName;

    @Column(name = "verification_provider", length = 30)
    private String verificationProvider;

    @Column(name = "provider_recipient_reference", length = 255)
    private String providerRecipientReference;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected RemittanceRecipientEntity() {}

    public RemittanceRecipientEntity(
        UUID id, UUID userId, String countryCode, PayoutMethod payoutMethod,
        UUID mobileMoneyNetworkId, String phoneNumber, String normalizedPhoneNumber,
        String verifiedName, String verificationProvider, String providerRecipientReference,
        Instant now
    ) {
        this.id = id;
        this.userId = userId;
        this.countryCode = countryCode;
        this.payoutMethod = payoutMethod;
        this.mobileMoneyNetworkId = mobileMoneyNetworkId;
        this.phoneNumber = phoneNumber;
        this.normalizedPhoneNumber = normalizedPhoneNumber;
        this.verifiedName = verifiedName;
        this.verificationProvider = verificationProvider;
        this.providerRecipientReference = providerRecipientReference;
        this.verifiedAt = now;
        this.active = true;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getCountryCode() { return countryCode; }
    public PayoutMethod getPayoutMethod() { return payoutMethod; }
    public UUID getMobileMoneyNetworkId() { return mobileMoneyNetworkId; }
    public String getPhoneNumber() { return phoneNumber; }
    public String getNormalizedPhoneNumber() { return normalizedPhoneNumber; }
    public String getVerifiedName() { return verifiedName; }
    public String getVerificationProvider() { return verificationProvider; }
    public String getProviderRecipientReference() { return providerRecipientReference; }
    public Instant getVerifiedAt() { return verifiedAt; }
    public boolean isActive() { return active; }

    public void refreshVerification(
        String phoneNumber, String normalizedPhoneNumber, String verifiedName,
        String verificationProvider, String providerRecipientReference, Instant now
    ) {
        this.phoneNumber = phoneNumber;
        this.normalizedPhoneNumber = normalizedPhoneNumber;
        this.verifiedName = verifiedName;
        this.verificationProvider = verificationProvider;
        this.providerRecipientReference = providerRecipientReference;
        this.verifiedAt = now;
        this.active = true;
        this.updatedAt = now;
    }
}
