package com.rackpay.api.persistence.admin;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "platform_admins")
public class PlatformAdminEntity {
    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "is_bootstrap_admin", nullable = false)
    private boolean bootstrapAdmin;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected PlatformAdminEntity() {}

    public PlatformAdminEntity(UUID userId, boolean bootstrapAdmin, Instant createdAt) {
        this.userId = userId;
        this.bootstrapAdmin = bootstrapAdmin;
        this.createdAt = createdAt;
    }

    public UUID getUserId() {
        return userId;
    }

    public boolean isBootstrapAdmin() {
        return bootstrapAdmin;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
