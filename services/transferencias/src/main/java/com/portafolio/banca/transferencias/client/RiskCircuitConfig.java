package com.portafolio.banca.transferencias.client;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JCircuitBreakerFactory;
import org.springframework.cloud.client.circuitbreaker.Customizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RiskCircuitConfig {
    private static final Logger log = LoggerFactory.getLogger(RiskCircuitConfig.class);

    @Bean
    Customizer<Resilience4JCircuitBreakerFactory> riskCircuit(
            @Value("${banca.transferencias.circuito.espera-abierto:15s}") Duration openWait,
            @Value("${banca.config.revision:local-fallback}") String configRevision) {
        log.info("Configuración no sensible de Transferencias cargada revision={}", configRevision);
        return factory -> factory.configure(
                builder -> builder.circuitBreakerConfig(CircuitBreakerConfig.custom()
                                .slidingWindowSize(10)
                                .minimumNumberOfCalls(3)
                                .failureRateThreshold(50)
                                .waitDurationInOpenState(openWait)
                                .build())
                        .build(),
                "riesgo");
    }
}
