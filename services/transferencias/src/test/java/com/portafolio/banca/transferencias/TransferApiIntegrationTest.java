package com.portafolio.banca.transferencias;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.portafolio.banca.transferencias.application.AccountsPort;
import com.portafolio.banca.transferencias.application.LedgerPort;
import com.portafolio.banca.transferencias.application.ReconciliationJob;
import com.portafolio.banca.transferencias.application.RiskPort;
import com.portafolio.banca.transferencias.application.TransferService;
import com.portafolio.banca.transferencias.application.TransferStore;
import com.portafolio.banca.transferencias.domain.AccountSnapshot;
import com.portafolio.banca.transferencias.domain.Transfer;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@Testcontainers
class TransferApiIntegrationTest {
    private static final UUID SOURCE = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa1");
    private static final UUID DESTINATION = UUID.fromString("cccccccc-cccc-4ccc-8ccc-ccccccccccc1");
    private static final String OWNER = "11111111-1111-4111-8111-111111111111";

    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    WebApplicationContext context;

    @Autowired
    TransferService service;

    @Autowired
    ReconciliationJob reconciliation;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    TransferStore store;

    @MockitoBean
    AccountsPort accounts;

    @MockitoBean
    LedgerPort ledger;

    @MockitoBean
    RiskPort risk;

    @Test
    void createsAndHidesTransfersByOwner() throws Exception {
        when(accounts.byId(SOURCE))
                .thenReturn(Optional.of(new AccountSnapshot(SOURCE, OWNER, "PEN", "ACTIVA", new BigDecimal("500.00"))));
        when(ledger.transfer(any())).thenReturn(new LedgerPort.LedgerResult("CONFIRMADA", "NONE"));
        when(risk.evaluate(any())).thenReturn(new RiskPort.RiskDecision("APROBADA", "NONE", "demo-v1"));
        var mvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
        String key = UUID.randomUUID().toString();
        String body = """
                {"cuentaOrigenId":"aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa1",
                 "cuentaDestinoId":"cccccccc-cccc-4ccc-8ccc-ccccccccccc1",
                 "importe":"10.00","moneda":"PEN"}
                """;
        var response = mvc.perform(post("/api/v1/transferencias")
                        .with(user(OWNER))
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("CONFIRMADA"))
                .andReturn();
        String id = com.jayway.jsonpath.JsonPath.read(response.getResponse().getContentAsString(), "$.id");
        mvc.perform(get("/api/v1/transferencias/{id}", id).with(user(OWNER))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/transferencias/{id}", id).with(user("22222222-2222-4222-8222-222222222222")))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/transferencias").with(user(OWNER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(id));
    }

    @Test
    void sameKeyInParallelCreatesOneTransferAndDifferentBodyConflicts() throws Exception {
        when(accounts.byId(SOURCE))
                .thenReturn(Optional.of(new AccountSnapshot(SOURCE, OWNER, "PEN", "ACTIVA", new BigDecimal("500.00"))));
        when(ledger.transfer(any())).thenReturn(new LedgerPort.LedgerResult("CONFIRMADA", "NONE"));
        when(risk.evaluate(any())).thenReturn(new RiskPort.RiskDecision("APROBADA", "NONE", "demo-v1"));
        UUID key = UUID.randomUUID();
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(10)) {
            Callable<UUID> request = () -> {
                start.await(5, TimeUnit.SECONDS);
                return service.create(OWNER, key, SOURCE, DESTINATION, "20.00", "PEN")
                        .transfer()
                        .id();
            };
            var futures = new java.util.ArrayList<java.util.concurrent.Future<UUID>>();
            for (int i = 0; i < 10; i++) {
                futures.add(pool.submit(request));
            }
            start.countDown();
            var ids = new java.util.HashSet<UUID>();
            for (var future : futures) {
                ids.add(future.get(10, TimeUnit.SECONDS));
            }
            org.assertj.core.api.Assertions.assertThat(ids).hasSize(1);
        }
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM transferencias WHERE owner_subject = ? AND idempotency_key = ?",
                Integer.class,
                OWNER,
                key);
        org.assertj.core.api.Assertions.assertThat(count).isEqualTo(1);
        verify(ledger, times(1)).transfer(any());
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> service.create(OWNER, key, SOURCE, DESTINATION, "21.00", "PEN"))
                .isInstanceOf(com.portafolio.banca.transferencias.application.IdempotencyConflictException.class);
        var mvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
        mvc.perform(post("/api/v1/transferencias")
                        .with(user(OWNER))
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"cuentaOrigenId":"aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa1",
                                 "cuentaDestinoId":"cccccccc-cccc-4ccc-8ccc-ccccccccccc1",
                                 "importe":"20.00","moneda":"PEN"}
                                """))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/transferencias")
                        .with(user(OWNER))
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"cuentaOrigenId":"aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa1",
                                 "cuentaDestinoId":"cccccccc-cccc-4ccc-8ccc-ccccccccccc1",
                                 "importe":"21.00","moneda":"PEN"}
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    void riskFailureRejectsWithoutCallingLedger() {
        when(accounts.byId(SOURCE))
                .thenReturn(
                        Optional.of(new AccountSnapshot(SOURCE, OWNER, "PEN", "ACTIVA", new BigDecimal("5000.00"))));
        when(risk.evaluate(any())).thenThrow(new IllegalStateException("simulated timeout"));
        var result = service.create(OWNER, UUID.randomUUID(), SOURCE, DESTINATION, "100.00", "PEN")
                .transfer();
        org.assertj.core.api.Assertions.assertThat(result.state()).isEqualTo("RECHAZADA");
        org.assertj.core.api.Assertions.assertThat(result.reasonCode()).isEqualTo("RIESGO_NO_DISPONIBLE");
        verify(ledger, never()).transfer(any());
    }

    @Test
    void riskRejectionRejectsWithoutCallingLedger() {
        when(accounts.byId(SOURCE))
                .thenReturn(
                        Optional.of(new AccountSnapshot(SOURCE, OWNER, "PEN", "ACTIVA", new BigDecimal("5000.00"))));
        when(risk.evaluate(any())).thenReturn(new RiskPort.RiskDecision("RECHAZADA", "RISK_REJECTED", "demo-v1"));
        var result = service.create(OWNER, UUID.randomUUID(), SOURCE, DESTINATION, "3000.01", "PEN")
                .transfer();
        org.assertj.core.api.Assertions.assertThat(result.state()).isEqualTo("RECHAZADA");
        org.assertj.core.api.Assertions.assertThat(result.reasonCode()).isEqualTo("RISK_REJECTED");
        verify(ledger, never()).transfer(any());
    }

    @Test
    void foreignSourceReturns404BeforeRiskAndLedger() throws Exception {
        when(accounts.byId(DESTINATION))
                .thenReturn(Optional.of(new AccountSnapshot(
                        DESTINATION,
                        "22222222-2222-4222-8222-222222222222",
                        "PEN",
                        "ACTIVA",
                        new BigDecimal("500.00"))));
        var mvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
        mvc.perform(post("/api/v1/transferencias")
                        .with(user(OWNER))
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"cuentaOrigenId":"cccccccc-cccc-4ccc-8ccc-ccccccccccc1",
                                 "cuentaDestinoId":"aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa1",
                                 "importe":"10.00","moneda":"PEN"}
                                """))
                .andExpect(status().isNotFound());
        verify(risk, never()).evaluate(any());
        verify(ledger, never()).transfer(any());
    }

    @Test
    void publicEndpointRejectsServiceIdentity() throws Exception {
        var mvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
        mvc.perform(get("/api/v1/cuentas")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/cuentas").with(jwt().jwt(token -> token.claim("azp", "transferencias-service"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void reconcilesLostLedgerResponseWithoutSecondCommand() {
        when(accounts.byId(SOURCE))
                .thenReturn(Optional.of(new AccountSnapshot(SOURCE, OWNER, "PEN", "ACTIVA", new BigDecimal("500.00"))));
        when(risk.evaluate(any())).thenReturn(new RiskPort.RiskDecision("APROBADA", "NONE", "demo-v1"));
        when(ledger.transfer(any()))
                .thenThrow(new org.springframework.web.client.ResourceAccessException("lost response"));
        var created = service.create(OWNER, UUID.randomUUID(), SOURCE, DESTINATION, "40.00", "PEN")
                .transfer();
        org.assertj.core.api.Assertions.assertThat(created.state()).isEqualTo("PENDIENTE_CONCILIACION");
        when(ledger.result(created.id())).thenReturn(Optional.of(new LedgerPort.LedgerResult("CONFIRMADA", "NONE")));
        jdbc.update(
                "UPDATE transferencias SET next_attempt_at = now() - interval '1 second' WHERE id = ?", created.id());
        reconciliation.reconcile();
        org.assertj.core.api.Assertions.assertThat(
                        service.own(OWNER, created.id()).state())
                .isEqualTo("CONFIRMADA");
        verify(ledger, times(1)).transfer(any());
    }

    @Test
    void recoversCrashInAccountingStateWithSameTransferId() {
        UUID id = UUID.randomUUID();
        UUID key = UUID.randomUUID();
        var interrupted = new Transfer(
                id,
                OWNER,
                key,
                "0".repeat(64),
                SOURCE,
                DESTINATION,
                new BigDecimal("50.00"),
                "PEN",
                "RECIBIDA",
                null,
                0,
                Instant.now(),
                Instant.now());
        store.insert(interrupted);
        store.state(id, "RECIBIDA", "EVALUANDO", null);
        store.state(id, "EVALUANDO", "CONTABILIZANDO", null);
        jdbc.update("UPDATE transferencias SET updated_at = now() - interval '10 seconds' WHERE id = ?", id);
        when(ledger.result(id)).thenReturn(Optional.empty());
        when(ledger.transfer(any())).thenReturn(new LedgerPort.LedgerResult("CONFIRMADA", "NONE"));
        reconciliation.reconcile();
        org.assertj.core.api.Assertions.assertThat(service.own(OWNER, id).state())
                .isEqualTo("CONFIRMADA");
        org.mockito.ArgumentCaptor<Transfer> command = org.mockito.ArgumentCaptor.forClass(Transfer.class);
        verify(ledger).transfer(command.capture());
        org.assertj.core.api.Assertions.assertThat(command.getValue().id()).isEqualTo(id);
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor user(String subject) {
        return jwt().jwt(token -> token.subject(subject).claim("azp", "banco-cli"));
    }
}
