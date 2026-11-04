package com.portafolio.banca.transferencias.client;

import com.portafolio.banca.transferencias.application.RiskPort;
import com.portafolio.banca.transferencias.domain.Transfer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class RiskClient implements RiskPort {
    private final RestClient client;
    private final ServiceTokenProvider tokens;
    private final CircuitBreakerFactory<?, ?> circuits;

    public RiskClient(
            RestClient.Builder builder,
            @Value("${clients.riesgo-url}") String url,
            ServiceTokenProvider tokens,
            CircuitBreakerFactory<?, ?> circuits) {
        this.client = builder.baseUrl(url).build();
        this.tokens = tokens;
        this.circuits = circuits;
    }

    @Override
    public RiskDecision evaluate(Transfer transfer) {
        return circuits.create("riesgo").run(() -> {
            RiskDecision response = client.post()
                    .uri("/internal/v1/evaluaciones")
                    .headers(headers -> headers.setBearerAuth(tokens.token()))
                    .body(new RiskCommand(
                            transfer.id(),
                            transfer.sourceAccountId(),
                            transfer.destinationAccountId(),
                            transfer.amount().toPlainString(),
                            transfer.currency()))
                    .retrieve()
                    .body(RiskDecision.class);
            if (response == null) {
                throw new IllegalStateException("Riesgo no devolvió decisión");
            }
            return response;
        });
    }

    private record RiskCommand(
            java.util.UUID transferId,
            java.util.UUID sourceAccountId,
            java.util.UUID destinationAccountId,
            String amount,
            String currency) {}
}
