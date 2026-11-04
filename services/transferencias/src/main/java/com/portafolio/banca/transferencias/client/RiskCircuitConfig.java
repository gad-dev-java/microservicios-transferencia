package com.portafolio.banca.transferencias.client;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import java.time.Duration;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JCircuitBreakerFactory;
import org.springframework.cloud.client.circuitbreaker.Customizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RiskCircuitConfig {
    @Bean
    Customizer<Resilience4JCircuitBreakerFactory> riskCircuit() {
        return factory -> factory.configure(
                builder -> builder.circuitBreakerConfig(CircuitBreakerConfig.custom()
                                .slidingWindowSize(10)
                                .minimumNumberOfCalls(3)
                                .failureRateThreshold(50)
                                .waitDurationInOpenState(Duration.ofSeconds(15))
                                .build())
                        .build(),
                "riesgo");
    }
}
