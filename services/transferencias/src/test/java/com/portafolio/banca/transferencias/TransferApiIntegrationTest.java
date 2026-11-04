package com.portafolio.banca.transferencias;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.portafolio.banca.transferencias.application.AccountsPort;
import com.portafolio.banca.transferencias.application.LedgerPort;
import com.portafolio.banca.transferencias.domain.AccountSnapshot;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
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

    @MockitoBean
    AccountsPort accounts;

    @MockitoBean
    LedgerPort ledger;

    @Test
    void createsAndHidesTransfersByOwner() throws Exception {
        when(accounts.byId(SOURCE))
                .thenReturn(Optional.of(new AccountSnapshot(SOURCE, OWNER, "PEN", "ACTIVA", new BigDecimal("500.00"))));
        when(ledger.transfer(any())).thenReturn(new LedgerPort.LedgerResult("CONFIRMADA", "NONE"));
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

    private static org.springframework.test.web.servlet.request.RequestPostProcessor user(String subject) {
        return jwt().jwt(token -> token.subject(subject).claim("azp", "banco-cli"));
    }
}
