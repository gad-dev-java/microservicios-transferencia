package com.portafolio.banca.historial.application;

import com.portafolio.banca.historial.domain.HistoryEntry;
import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

@Service
public class HistoryService {
    private final HistoryStore store;
    private final JsonMapper json;
    private final MeterRegistry meters;

    public HistoryService(HistoryStore store, JsonMapper json, MeterRegistry meters) {
        this.store = store;
        this.json = json;
        this.meters = meters;
    }

    public void receive(String payload) {
        var node = json.readTree(payload);
        if (node.path("schemaVersion").asInt() != 1
                || !"TransferenciaConfirmada".equals(node.path("eventType").asText())
                || node.path("actorSubject").asText().isBlank()
                || !"PEN".equals(node.path("currency").asText())
                || !node.path("amount").asText().matches("(0|[1-9][0-9]{0,16})\\.[0-9]{2}")
                || new BigDecimal(node.path("amount").asText()).signum() <= 0) {
            throw new IllegalArgumentException("Evento no compatible");
        }
        var entry = new HistoryEntry(
                UUID.fromString(node.path("eventId").asText()),
                UUID.fromString(node.path("transferId").asText()),
                node.path("actorSubject").asText(),
                UUID.fromString(node.path("sourceAccountId").asText()),
                UUID.fromString(node.path("destinationAccountId").asText()),
                node.path("amount").asText(),
                node.path("currency").asText(),
                Instant.parse(node.path("occurredAt").asText()),
                hash(payload));
        if (store.add(entry)) {
            meters.counter("historial.eventos.proyectados").increment();
        }
    }

    public List<HistoryEntry> search(String owner, Instant from, Instant to, UUID accountId, int limit) {
        if (limit < 1 || limit > 100 || (from != null && to != null && from.isAfter(to))) {
            throw new IllegalArgumentException("Filtros de historial inválidos");
        }
        return store.search(owner, from, to, accountId, limit);
    }

    private String hash(String payload) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
