package com.portafolio.banca.notificaciones;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.portafolio.banca.notificaciones.application.NotificationService;
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

@SpringBootTest(properties = "spring.kafka.listener.auto-startup=false")
@Testcontainers
class NotificationIntegrationTest {
    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    NotificationService notifications;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void repeatedEventCreatesOneDeliveryForItsOwner() {
        UUID eventId = UUID.randomUUID();
        String payload = """
                {"eventId":"%s","eventType":"TransferenciaConfirmada","schemaVersion":1,
                 "transferId":"%s","actorSubject":"owner-1",
                 "sourceAccountId":"aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa1",
                 "destinationAccountId":"cccccccc-cccc-4ccc-8ccc-ccccccccccc1",
                 "amount":"1.00","currency":"PEN","occurredAt":"2026-11-04T00:00:00Z"}
                """.formatted(eventId, UUID.randomUUID());
        notifications.receive(payload);
        notifications.receive(payload);
        assertThat(notifications.own("owner-1")).hasSize(1);
        assertThat(notifications.own("owner-2")).isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM entregas WHERE event_id = ?", Integer.class, eventId))
                .isEqualTo(1);
        assertThatThrownBy(() -> notifications.receive(payload.replace("1.00", "2.00")))
                .isInstanceOf(DuplicateKeyException.class);
    }
}
