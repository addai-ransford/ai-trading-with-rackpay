package com.rackpay.api.persistence.user;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users", uniqueConstraints = {
    @UniqueConstraint(name = "uq_user_keycloak_subject", columnNames = "keycloak_subject"),
    @UniqueConstraint(name = "uq_user_email", columnNames = "email")
})
public class UserEntity {
    @Id
    private UUID id;

    @Column(name = "keycloak_subject", nullable = false, length = 255)
    private String keycloakSubject;

    @Column(nullable = false, length = 320)
    private String email;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(length = 30)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UserEntity() {}

    public UserEntity(UUID id, String keycloakSubject, String email, String firstName,
                      String lastName, String phone, UserStatus status, Instant createdAt) {
        this.id = id;
        this.keycloakSubject = keycloakSubject;
        this.email = email;
        this.firstName = firstName;
        this.lastName = lastName;
        this.phone = phone;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    public UUID getId() { return id; }
    public String getKeycloakSubject() { return keycloakSubject; }
    public String getEmail() { return email; }
    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public String getPhone() { return phone; }
    public UserStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}

