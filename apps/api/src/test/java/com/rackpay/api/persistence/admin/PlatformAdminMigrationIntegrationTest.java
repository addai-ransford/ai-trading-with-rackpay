package com.rackpay.api.persistence.admin;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Testcontainers
class PlatformAdminMigrationIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void migrationsCreatePlatformAdminsAndAllowOnlyOneBootstrapAdmin() throws Exception {
        Flyway.configure()
            .dataSource(
                POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword()
            )
            .locations("classpath:db/migration")
            .load()
            .migrate();

        UUID firstUserId = UUID.randomUUID();
        UUID secondUserId = UUID.randomUUID();

        try (Connection connection = POSTGRES.createConnection("")) {
            insertUser(connection, firstUserId, "subject-1", "first@rackpay.local");
            insertUser(connection, secondUserId, "subject-2", "second@rackpay.local");

            insertPlatformAdmin(connection, firstUserId, true);

            assertThrows(
                SQLException.class,
                () -> insertPlatformAdmin(connection, secondUserId, true)
            );

            assertEquals(1, countBootstrapAdmins(connection));
        }
    }

    private static void insertUser(
        Connection connection,
        UUID id,
        String subject,
        String email
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            INSERT INTO users (
                id,
                keycloak_subject,
                email,
                first_name,
                last_name,
                status,
                created_at,
                updated_at
            )
            VALUES (?, ?, ?, 'Test', 'User', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            """)) {
            statement.setObject(1, id);
            statement.setString(2, subject);
            statement.setString(3, email);
            statement.executeUpdate();
        }
    }

    private static void insertPlatformAdmin(
        Connection connection,
        UUID userId,
        boolean bootstrap
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            INSERT INTO platform_admins (user_id, is_bootstrap_admin)
            VALUES (?, ?)
            """)) {
            statement.setObject(1, userId);
            statement.setBoolean(2, bootstrap);
            statement.executeUpdate();
        }
    }

    private static int countBootstrapAdmins(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            SELECT COUNT(*)
            FROM platform_admins
            WHERE is_bootstrap_admin = TRUE
            """);
             var result = statement.executeQuery()) {
            result.next();
            return result.getInt(1);
        }
    }
}
