package com.portafolio.banca.riesgo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.portafolio.banca.riesgo.api.EvaluationRequest;
import com.portafolio.banca.riesgo.application.RiskService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@Testcontainers
class RiskServiceIntegrationTest {
    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    RiskService service;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void thresholdIsDeterministicAndDecisionIsPersistent() {
        UUID id = UUID.randomUUID();
        var approved = request(id, "3000.00");
        assertThat(service.evaluate(approved).decision()).isEqualTo("APROBADA");
        assertThat(service.evaluate(approved).ruleVersion()).isEqualTo("demo-v1");
        assertThat(service.evaluate(request(UUID.randomUUID(), "3000.01")).decision())
                .isEqualTo("RECHAZADA");
        assertThatThrownBy(() -> service.evaluate(request(id, "2999.99"))).isInstanceOf(DuplicateKeyException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM evaluaciones WHERE transfer_id = ?", Integer.class, id))
                .isEqualTo(1);
    }

    private EvaluationRequest request(UUID id, String amount) {
        return new EvaluationRequest(
                id,
                UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa1"),
                UUID.fromString("cccccccc-cccc-4ccc-8ccc-ccccccccccc1"),
                amount,
                "PEN");
    }
}
