package com.portafolio.banca.historial;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.portafolio.banca.historial.application.HistoryService;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;

@SpringBootTest(properties = "spring.kafka.listener.auto-startup=false")
@Testcontainers
class HistoryIntegrationTest {
    @Container
    static MongoDBContainer mongo = new MongoDBContainer("mongo:8.0.32").withReplicaSet();

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.mongodb.uri", mongo::getConnectionString);
        registry.add("spring.mongodb.database", () -> "historial_test");
    }

    @Autowired
    HistoryService history;

    @Autowired
    MongoTemplate mongoTemplate;

    @Autowired
    WebApplicationContext context;

    @Test
    void duplicateIsHarmlessAndSearchIsLimitedToOwner() throws Exception {
        UUID eventId = UUID.randomUUID();
        String payload = """
                {"eventId":"%s","eventType":"TransferenciaConfirmada","schemaVersion":1,
                 "transferId":"%s","actorSubject":"owner-1",
                 "sourceAccountId":"aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa1",
                 "destinationAccountId":"cccccccc-cccc-4ccc-8ccc-ccccccccccc1",
                 "amount":"1.00","currency":"PEN","occurredAt":"2026-11-04T00:00:00Z"}
                """.formatted(eventId, UUID.randomUUID());
        history.receive(payload);
        history.receive(payload);
        assertThat(mongoTemplate.getCollection("transferencias_confirmadas").countDocuments())
                .isEqualTo(1);
        assertThat(history.search("owner-1", null, null, null, 20)).hasSize(1);
        assertThat(history.search("owner-2", null, null, null, 20)).isEmpty();
        assertThat(history.search("owner-1", Instant.parse("2026-11-05T00:00:00Z"), null, null, 20))
                .isEmpty();
        assertThat(history.search("owner-1", null, null, UUID.randomUUID(), 20)).isEmpty();
        assertThatThrownBy(() -> history.receive(payload.replace("1.00", "2.00")))
                .isInstanceOf(DuplicateKeyException.class);

        var mvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
        mvc.perform(get("/api/v1/historial")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/historial")
                        .with(jwt().jwt(token -> token.subject("owner-2").claim("azp", "banco-cli"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
        mvc.perform(get("/api/v1/historial")
                        .with(jwt().jwt(token -> token.subject("owner-1").claim("azp", "banco-cli"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].eventId").value(eventId.toString()))
                .andExpect(jsonPath("$[0].eventHash").doesNotExist());
    }
}
