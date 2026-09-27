package com.rackpay.api.remittance;

import com.rackpay.api.persistence.remittance.RemittanceEntity;
import com.rackpay.api.persistence.remittance.RemittanceJpaRepository;
import com.rackpay.api.persistence.remittance.RemittanceRecipientJpaRepository;
import com.rackpay.api.persistence.user.CurrentUserService;
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
            .map(remittance -> {
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
            })
            .toList();
    }

    public record RemittanceResponse(
        UUID remittanceId,
        String recipientName,
        RemittanceEntity.Status status,
        BigDecimal sourceAmount,
        com.rackpay.api.domain.money.Currency sourceCurrency,
        BigDecimal feeAmount,
        BigDecimal destinationAmount,
        com.rackpay.api.domain.money.Currency destinationCurrency,
        String payoutProvider,
        String providerTransferId,
        Instant createdAt,
        Instant updatedAt
    ) {}
}
