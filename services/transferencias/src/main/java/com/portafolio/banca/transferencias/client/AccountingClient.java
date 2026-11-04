package com.portafolio.banca.transferencias.client;

import com.portafolio.banca.transferencias.application.AccountsPort;
import com.portafolio.banca.transferencias.domain.AccountSnapshot;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public class AccountingClient implements AccountsPort {
    private final RestClient client;
    private final ServiceTokenProvider tokens;

    public AccountingClient(
            RestClient.Builder builder, @Value("${clients.contabilidad-url}") String url, ServiceTokenProvider tokens) {
        this.client = builder.baseUrl(url).build();
        this.tokens = tokens;
    }

    @Override
    public List<AccountSnapshot> byOwner(String ownerSubject) {
        List<InternalAccount> response = client.get()
                .uri(uri -> uri.path("/internal/v1/cuentas")
                        .queryParam("ownerSubject", ownerSubject)
                        .build())
                .headers(headers -> headers.setBearerAuth(tokens.token()))
                .retrieve()
                .body(new ParameterizedTypeReference<List<InternalAccount>>() {});
        return response == null
                ? List.of()
                : response.stream().map(InternalAccount::snapshot).toList();
    }

    @Override
    public Optional<AccountSnapshot> byId(UUID id) {
        try {
            InternalAccount response = client.get()
                    .uri("/internal/v1/cuentas/{id}", id)
                    .headers(headers -> headers.setBearerAuth(tokens.token()))
                    .retrieve()
                    .body(InternalAccount.class);
            return response == null ? Optional.empty() : Optional.of(response.snapshot());
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 404) {
                return Optional.empty();
            }
            throw exception;
        }
    }

    public record InternalAccount(
            UUID accountId, String ownerSubject, String currency, String status, BigDecimal balance) {
        AccountSnapshot snapshot() {
            return new AccountSnapshot(accountId, ownerSubject, currency, status, balance);
        }
    }
}
