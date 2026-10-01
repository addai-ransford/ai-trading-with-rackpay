package com.rackpay.api.remittance.core.service;

import com.rackpay.api.remittance.adapters.out.persistence.RemittanceEntity;
import com.rackpay.api.remittance.adapters.out.persistence.RemittanceJpaRepository;
import com.rackpay.api.remittance.adapters.out.persistence.RemittanceRecipientJpaRepository;
import com.rackpay.api.user.adapters.out.persistence.CurrentUserService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class RemittanceHistoryService {
    private final CurrentUserService currentUser;
    private final RemittanceJpaRepository remittances;
    private final RemittanceRecipientJpaRepository recipients;

    public RemittanceHistoryService(
        CurrentUserService currentUser,
        RemittanceJpaRepository remittances,
        RemittanceRecipientJpaRepository recipients
    ) {
        this.currentUser = currentUser;
        this.remittances = remittances;
        this.recipients = recipients;
    }

    public List<RemittanceResponse> list(Authentication authentication) {
        UUID userId = currentUser.requireUserId(authentication);

        return remittances.findAllByUserIdOrderByCreatedAtDesc(userId).stream()
            .map(this::toResponse)
            .toList();
    }

    public RemittanceResponse get(Authentication authentication, UUID remittanceId) {
        UUID userId = currentUser.requireUserId(authentication);

        RemittanceEntity remittance = remittances.findByUserIdAndId(userId, remittanceId)
            .orElseThrow(() -> new IllegalArgumentException("remittance not found"));

        return toResponse(remittance);
    }

    private RemittanceResponse toResponse(RemittanceEntity remittance) {
        String recipientName = recipients.findById(remittance.getRecipientId())
            .map(r -> r.getVerifiedName())
            .orElse(null);

        return new RemittanceResponse(
            remittance.getId(),
            recipientName,
            remittance.getStatus(),
            remittance.getSourceAmount(),
            remittance.getSourceCurrency(),
            remittance.getFeeAmount(),
            remittance.getDestinationAmount(),
            remittance.getDestinationCurrency(),
            remittance.getPayoutProvider(),
            remittance.getProviderTransferId(),
            remittance.getCreatedAt(),
            remittance.getUpdatedAt()
        );
    }

    public record RemittanceResponse(
        UUID remittanceId,
        String recipientName,
        RemittanceEntity.Status status,
        BigDecimal sourceAmount,
        com.rackpay.api.shared.core.money.Currency sourceCurrency,
        BigDecimal feeAmount,
        BigDecimal destinationAmount,
        com.rackpay.api.shared.core.money.Currency destinationCurrency,
        String payoutProvider,
        String providerTransferId,
        Instant createdAt,
        Instant updatedAt
    ) {}
}
