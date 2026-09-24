package com.rackpay.api.auth;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Component
public class KeycloakAdminClient {
    private final RestClient restClient;
    private final KeycloakAdminProperties properties;

    public KeycloakAdminClient(RestClient.Builder builder, KeycloakAdminProperties properties) {
        this.properties = properties;
        this.restClient = builder.baseUrl(properties.baseUrl()).build();
    }

    public String createUser(String email, String firstName, String lastName, String phone, String password) {
        String token = serviceAccountToken();

        KeycloakUserRequest request = new KeycloakUserRequest(
            email, email, firstName, lastName, true, false,
            Map.of("phone", new String[]{phone == null ? "" : phone})
        );

        var response = restClient.post()
            .uri("/admin/realms/{realm}/users", properties.realm())
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .body(request)
            .retrieve()
            .toBodilessEntity();

        String location = response.getHeaders().getFirst("Location");
        if (location == null || location.isBlank()) {
            throw new IllegalStateException("Keycloak did not return the created user location");
        }

        String userId = location.substring(location.lastIndexOf('/') + 1);
        setPassword(userId, password, token);
        return userId;
    }

    public void deleteUser(String userId) {
        String token = serviceAccountToken();
        restClient.delete()
            .uri("/admin/realms/{realm}/users/{userId}", properties.realm(), userId)
            .header("Authorization", "Bearer " + token)
            .retrieve()
            .toBodilessEntity();
    }

    private void setPassword(String userId, String password, String token) {
        var credential = Map.of(
            "type", "password",
            "value", password,
            "temporary", false
        );

        restClient.put()
            .uri("/admin/realms/{realm}/users/{userId}/reset-password", properties.realm(), userId)
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .body(credential)
            .retrieve()
            .toBodilessEntity();
    }

    private String serviceAccountToken() {
        var form = new LinkedMultiValueMap<String, String>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", properties.clientId());
        form.add("client_secret", properties.clientSecret());

        TokenResponse response = restClient.post()
            .uri("/realms/{realm}/protocol/openid-connect/token", properties.realm())
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(form)
            .retrieve()
            .body(TokenResponse.class);

        if (response == null || response.accessToken() == null || response.accessToken().isBlank()) {
            throw new IllegalStateException("Keycloak service-account authentication failed");
        }

        return response.accessToken();
    }

    private record TokenResponse(String access_token) {
        String accessToken() { return access_token; }
    }

    private record KeycloakUserRequest(
        String username,
        String email,
        String firstName,
        String lastName,
        boolean enabled,
        boolean emailVerified,
        Map<String, String[]> attributes
    ) {}
}
