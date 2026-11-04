package com.portafolio.banca.transferencias.application;

import com.portafolio.banca.transferencias.domain.Transfer;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

@Service
public class TransferService {
    private static final Logger log = LoggerFactory.getLogger(TransferService.class);
    private final TransferStore store;
    private final AccountsService accounts;
    private final LedgerPort ledger;
    private final RiskPort risk;

    public TransferService(TransferStore store, AccountsService accounts, LedgerPort ledger, RiskPort risk) {
        this.store = store;
        this.accounts = accounts;
        this.ledger = ledger;
        this.risk = risk;
    }

    public Creation create(String owner, UUID key, UUID source, UUID destination, String amount, String currency) {
        BigDecimal value = validate(source, destination, amount, currency);
        String hash = fingerprint(source, destination, value, currency);
        var prior = store.byKey(owner, key);
        if (prior.isPresent()) {
            return existing(prior.get(), hash);
        }
        accounts.ownAccount(owner, source);
        UUID id = UUID.randomUUID();
        var candidate = new Transfer(
                id,
                owner,
                key,
                hash,
                source,
                destination,
                value,
                currency,
                "RECIBIDA",
                null,
                0,
                Instant.now(),
                Instant.now());
        if (!store.insert(candidate)) {
            return existing(store.byKey(owner, key).orElseThrow(), hash);
        }
        store.state(id, "RECIBIDA", "EVALUANDO", null);
        try {
            RiskPort.RiskDecision decision = risk.evaluate(candidate);
            if (!"APROBADA".equals(decision.decision())) {
                store.state(id, "EVALUANDO", "RECHAZADA", "RISK_REJECTED");
                return new Creation(store.byId(id).orElseThrow(), true);
            }
        } catch (RuntimeException exception) {
            log.warn("Riesgo no disponible transferId={}", id);
            store.state(id, "EVALUANDO", "RECHAZADA", "RIESGO_NO_DISPONIBLE");
            return new Creation(store.byId(id).orElseThrow(), true);
        }
        store.state(id, "EVALUANDO", "CONTABILIZANDO", null);
        try {
            LedgerPort.LedgerResult result = ledger.transfer(candidate);
            store.state(id, "CONTABILIZANDO", result.outcome(), result.reasonCode());
        } catch (RestClientException exception) {
            log.warn("Resultado contable incierto transferId={}", id);
            store.state(id, "CONTABILIZANDO", "PENDIENTE_CONCILIACION", null);
        }
        return new Creation(store.byId(id).orElseThrow(), true);
    }

    public Transfer own(String owner, UUID id) {
        Transfer transfer = store.byId(id).orElseThrow(ResourceNotFoundException::new);
        if (!owner.equals(transfer.ownerSubject())) {
            throw new ResourceNotFoundException();
        }
        return transfer;
    }

    public List<Transfer> page(String owner, Instant beforeTime, UUID beforeId, int limit) {
        if (limit < 1 || limit > 101) {
            throw new InvalidTransferException("limit debe estar entre 1 y 100");
        }
        return store.page(owner, beforeTime, beforeId, limit);
    }

    private Creation existing(Transfer transfer, String hash) {
        if (!transfer.requestHash().equals(hash)) {
            throw new IdempotencyConflictException();
        }
        return new Creation(transfer, false);
    }

    private BigDecimal validate(UUID source, UUID destination, String amount, String currency) {
        if (source == null || destination == null || source.equals(destination)) {
            throw new InvalidTransferException("Cuentas inválidas");
        }
        if (amount == null || !amount.matches("(0|[1-9][0-9]{0,16})\\.[0-9]{2}")) {
            throw new InvalidTransferException("Importe inválido");
        }
        BigDecimal value = new BigDecimal(amount);
        if (value.signum() <= 0 || !"PEN".equals(currency)) {
            throw new InvalidTransferException("Importe o moneda inválidos");
        }
        return value;
    }

    private String fingerprint(UUID source, UUID destination, BigDecimal amount, String currency) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((source + "|" + destination + "|" + amount.toPlainString() + "|" + currency)
                            .getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    public record Creation(Transfer transfer, boolean created) {}
}
