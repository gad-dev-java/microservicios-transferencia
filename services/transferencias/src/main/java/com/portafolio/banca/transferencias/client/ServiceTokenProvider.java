package com.portafolio.banca.transferencias.client;

import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;

@Component
public class ServiceTokenProvider {
    private final RestClient client;
    private final String secret;
    private String cachedToken;
    private Instant expiresAt = Instant.EPOCH;

    public ServiceTokenProvider(
            RestClient.Builder builder,
            @Value("${clients.token-url}") String tokenUrl,
            @Value("${clients.service-client-secret}") String secret) {
        this.client = builder.baseUrl(tokenUrl).build();
        this.secret = secret;
    }

    public synchronized String token() {
        if (cachedToken != null && Instant.now().isBefore(expiresAt)) {
            return cachedToken;
        }
        var form = new LinkedMultiValueMap<String, String>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", "transferencias-service");
        form.add("client_secret", secret);
        TokenResponse response = client.post()
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(TokenResponse.class);
        if (response == null || response.access_token() == null || response.expires_in() <= 0) {
            throw new IllegalStateException("Keycloak no devolvió un token válido");
        }
        cachedToken = response.access_token();
        expiresAt = Instant.now().plusSeconds(Math.max(1, response.expires_in() - 15));
        return cachedToken;
    }

    private record TokenResponse(String access_token, long expires_in) {}
}
