package com.portafolio.banca.contabilidad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.portafolio.banca.contabilidad.application.AccountingService;
import com.portafolio.banca.contabilidad.application.IdempotencyConflictException;
import com.portafolio.banca.contabilidad.application.TransferResult;
import com.portafolio.banca.contabilidad.domain.TransferInstruction;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@Testcontainers
class AccountingServiceIntegrationTest {
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
    AccountingService accounting;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    WebApplicationContext webContext;

    @BeforeEach
    void seed() {
        jdbc.update("DELETE FROM outbox");
        jdbc.update("DELETE FROM asientos");
        jdbc.update("DELETE FROM comandos");
        jdbc.update("DELETE FROM movimientos");
        jdbc.update("DELETE FROM cuentas");
        jdbc.update("""
                INSERT INTO cuentas (id, owner_subject, currency, status, opening_balance, balance)
                VALUES (?, ?, 'PEN', 'ACTIVA', 4000.00, 4000.00)
                """, SOURCE, OWNER);
        jdbc.update("""
                INSERT INTO cuentas (id, owner_subject, currency, status, opening_balance, balance)
                VALUES (?, ?, 'PEN', 'ACTIVA', 200.00, 200.00)
                """, DESTINATION, "22222222-2222-4222-8222-222222222222");
    }

    @Test
    void listsOnlyAccountsOwnedByRequestedSubject() {
        var mvc = MockMvcBuilders.webAppContextSetup(webContext)
                .apply(springSecurity())
                .build();
        assertThat(accounting.accounts(OWNER))
                .extracting(account -> account.ownerSubject())
                .containsOnly(OWNER);
        try {
            mvc.perform(get("/internal/v1/cuentas").param("ownerSubject", OWNER).with(serviceJwt()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].accountId").value(SOURCE.toString()))
                    .andExpect(jsonPath("$[1]").doesNotExist());
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    @Test
    void confirmsWithExactlyTwoBalancedEntries() {
        UUID transferId = UUID.randomUUID();
        TransferResult result = accounting.transfer(command(transferId, "100.00"));

        assertThat(result.outcome()).isEqualTo("CONFIRMADA");
        assertThat(result.movementId()).isNotNull();
        assertThat(accounting.result(transferId)).isEqualTo(result);
        assertBalance(SOURCE, "3900.00");
        assertBalance(DESTINATION, "300.00");
        assertThat(count("movimientos")).isEqualTo(1);
        assertThat(count("asientos")).isEqualTo(2);
        assertThat(count("outbox")).isEqualTo(1);
        String event =
                jdbc.queryForObject("SELECT payload::text FROM outbox WHERE transfer_id = ?", String.class, transferId);
        assertThat(event).contains("TransferenciaConfirmada", transferId.toString(), OWNER, "100.00");
        List<BigDecimal> deltas = jdbc.query(
                "SELECT delta FROM asientos WHERE movement_id = ? ORDER BY delta",
                (rs, row) -> rs.getBigDecimal(1),
                result.movementId());
        assertThat(deltas).containsExactly(new BigDecimal("-100.00"), new BigDecimal("100.00"));
    }

    @Test
    void rejectsInsufficientFundsAndRemembersRejection() {
        UUID id = UUID.randomUUID();
        TransferResult result = accounting.transfer(command(id, "4000.01"));
        assertThat(count("outbox")).isZero();

        assertThat(result.outcome()).isEqualTo("RECHAZADA");
        assertThat(result.reasonCode()).isEqualTo("INSUFFICIENT_FUNDS");
        assertThat(accounting.transfer(command(id, "4000.01"))).isEqualTo(result);
        assertThat(accounting.result(id)).isEqualTo(result);
        assertBalance(SOURCE, "4000.00");
        assertBalance(DESTINATION, "200.00");
        assertThat(count("movimientos")).isZero();
        assertThat(count("asientos")).isZero();
    }

    @Test
    void rejectsInactiveAccountWithoutMovement() {
        jdbc.update("UPDATE cuentas SET status = 'INACTIVA' WHERE id = ?", DESTINATION);

        TransferResult result = accounting.transfer(command(UUID.randomUUID(), "100.00"));

        assertThat(result.reasonCode()).isEqualTo("ACCOUNT_INACTIVE");
        assertBalance(SOURCE, "4000.00");
        assertBalance(DESTINATION, "200.00");
        assertThat(count("asientos")).isZero();
    }

    @Test
    void repeatedTransferIdWithChangedPayloadConflicts() {
        UUID id = UUID.randomUUID();
        accounting.transfer(command(id, "100.00"));

        assertThatThrownBy(() -> accounting.transfer(command(id, "101.00")))
                .isInstanceOf(IdempotencyConflictException.class);
        assertThat(count("movimientos")).isEqualTo(1);
        assertBalance(SOURCE, "3900.00");
    }

    @Test
    void concurrentRetriesCreateOnlyOneMovement() throws Exception {
        UUID id = UUID.randomUUID();
        List<TransferResult> results = concurrent(10, index -> command(id, "100.00"));

        assertThat(results)
                .hasSize(10)
                .allMatch(result -> "CONFIRMADA".equals(result.outcome()))
                .allMatch(
                        result -> result.movementId().equals(results.getFirst().movementId()));
        assertThat(count("movimientos")).isEqualTo(1);
        assertThat(count("asientos")).isEqualTo(2);
        assertBalance(SOURCE, "3900.00");
    }

    @Test
    void concurrentTransfersCannotOverdrawSource() throws Exception {
        List<TransferResult> results = concurrent(2, index -> command(UUID.randomUUID(), "2500.00"));

        assertThat(results.stream()
                        .filter(result -> "CONFIRMADA".equals(result.outcome()))
                        .count())
                .isEqualTo(1);
        assertThat(results.stream()
                        .filter(result -> "RECHAZADA".equals(result.outcome()))
                        .count())
                .isEqualTo(1);
        assertBalance(SOURCE, "1500.00");
        assertBalance(DESTINATION, "2700.00");
        assertThat(count("movimientos")).isEqualTo(1);
        assertThat(count("asientos")).isEqualTo(2);
    }

    @Test
    void databaseFailureRollsBackAllWrites() {
        jdbc.update("UPDATE cuentas SET balance = 99999999999999999.99 WHERE id = ?", DESTINATION);

        assertThatThrownBy(() -> accounting.transfer(command(UUID.randomUUID(), "100.00")))
                .isInstanceOf(DataAccessException.class);
        assertBalance(SOURCE, "4000.00");
        assertThat(count("comandos")).isZero();
        assertThat(count("movimientos")).isZero();
        assertThat(count("asientos")).isZero();
    }

    @Test
    void httpErrorsUseProblemDetails() throws Exception {
        var mvc = MockMvcBuilders.webAppContextSetup(webContext)
                .apply(springSecurity())
                .build();
        var id = UUID.randomUUID();
        var body = """
                {"transferId":"%s","actorSubject":"%s","sourceAccountId":"%s",
                 "destinationAccountId":"%s","amount":"100.00","currency":"PEN"}
                """.formatted(id, OWNER, SOURCE, DESTINATION);
        var changedBody = body.replace("100.00", "101.00");

        mvc.perform(get("/internal/v1/movimientos/transferencia/{id}", id).with(serviceJwt()))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        mvc.perform(post("/internal/v1/movimientos/transferencia")
                        .with(serviceJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
        mvc.perform(post("/internal/v1/movimientos/transferencia")
                        .with(serviceJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(changedBody))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));
    }

    @Test
    void internalRoutesRequireTransferenciasServiceIdentity() throws Exception {
        var mvc = MockMvcBuilders.webAppContextSetup(webContext)
                .apply(springSecurity())
                .build();

        mvc.perform(get("/internal/v1/cuentas/{id}", SOURCE)).andExpect(status().isUnauthorized());
        mvc.perform(get("/internal/v1/cuentas/{id}", SOURCE).with(jwt().jwt(token -> token.claim("azp", "banco-cli"))))
                .andExpect(status().isForbidden());
        mvc.perform(get("/internal/v1/cuentas/{id}", SOURCE).with(serviceJwt())).andExpect(status().isOk());
    }

    private static RequestPostProcessor serviceJwt() {
        return jwt().jwt(token -> token.claim("azp", "transferencias-service"));
    }

    private List<TransferResult> concurrent(int requests, java.util.function.IntFunction<TransferInstruction> commands)
            throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(requests);
        try {
            List<Future<TransferResult>> futures = new ArrayList<>();
            for (int i = 0; i < requests; i++) {
                TransferInstruction command = commands.apply(i);
                Callable<TransferResult> task = () -> {
                    start.await();
                    return accounting.transfer(command);
                };
                futures.add(pool.submit(task));
            }
            start.countDown();
            List<TransferResult> results = new ArrayList<>();
            for (Future<TransferResult> future : futures) {
                results.add(future.get(30, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    private static TransferInstruction command(UUID id, String amount) {
        return new TransferInstruction(id, OWNER, SOURCE, DESTINATION, new BigDecimal(amount), "PEN");
    }

    private void assertBalance(UUID account, String expected) {
        assertThat(accounting.account(account).balance()).isEqualByComparingTo(expected);
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class);
    }
}
