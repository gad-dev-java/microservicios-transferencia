package com.portafolio.banca.notificaciones.application;

import io.micrometer.core.instrument.MeterRegistry;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

@Service
public class NotificationService {
    private final JdbcTemplate jdbc;
    private final JsonMapper json;
    private final MeterRegistry meters;

    public NotificationService(JdbcTemplate jdbc, JsonMapper json, MeterRegistry meters) {
        this.jdbc = jdbc;
        this.json = json;
        this.meters = meters;
    }

    public void receive(String payload) {
        var node = json.readTree(payload);
        if (node.path("schemaVersion").asInt() != 1
                || !"TransferenciaConfirmada".equals(node.path("eventType").asText())
                || node.path("actorSubject").asText().isBlank()) {
            throw new IllegalArgumentException("Evento no compatible");
        }
        UUID eventId = UUID.fromString(node.path("eventId").asText());
        UUID transferId = UUID.fromString(node.path("transferId").asText());
        String owner = node.path("actorSubject").asText();
        String hash = hash(payload);
        int inserted = jdbc.update("""
                INSERT INTO entregas(event_id, transfer_id, owner_subject, event_hash, status)
                VALUES (?, ?, ?, ?, 'SIMULADA') ON CONFLICT(event_id) DO NOTHING
                """, eventId, transferId, owner, hash);
        if (inserted == 0) {
            String saved =
                    jdbc.queryForObject("SELECT event_hash FROM entregas WHERE event_id = ?", String.class, eventId);
            if (!hash.equals(saved)) {
                throw new DuplicateKeyException("eventId reutilizado con otro contenido");
            }
        } else {
            meters.counter("notificaciones.entregas.simuladas").increment();
        }
    }

    public List<Delivery> own(String subject) {
        return jdbc.query(
                """
                SELECT event_id, transfer_id, status, created_at FROM entregas
                WHERE owner_subject = ? ORDER BY created_at DESC, event_id DESC LIMIT 100
                """,
                (rs, row) -> new Delivery(
                        rs.getObject("event_id", UUID.class),
                        rs.getObject("transfer_id", UUID.class),
                        rs.getString("status"),
                        rs.getTimestamp("created_at").toInstant()),
                subject);
    }

    private String hash(String payload) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    public record Delivery(UUID eventId, UUID transferenciaId, String estado, Instant creadaEn) {}
}
