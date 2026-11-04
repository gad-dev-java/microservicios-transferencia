package com.portafolio.banca.contabilidad.infrastructure.kafka;

import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "outbox.enabled", havingValue = "true")
public class OutboxRelay {
    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    private static final String TOPIC = "transferencias.confirmadas.v1";
    private final JdbcTemplate jdbc;
    private final KafkaTemplate<String, String> kafka;
    private final MeterRegistry meters;

    public OutboxRelay(JdbcTemplate jdbc, KafkaTemplate<String, String> kafka, MeterRegistry meters) {
        this.jdbc = jdbc;
        this.kafka = kafka;
        this.meters = meters;
        meters.gauge("contabilidad.outbox.pending", this, relay -> relay.pending());
        meters.gauge("contabilidad.outbox.oldest_age_seconds", this, relay -> relay.oldestAgeSeconds());
    }

    @Scheduled(fixedDelayString = "${outbox.delay-ms:1000}")
    public void relay() {
        for (Event event : claim(20)) {
            try {
                kafka.send(TOPIC, event.transferId().toString(), event.payload())
                        .get(6, TimeUnit.SECONDS);
                jdbc.update(
                        "UPDATE outbox SET published_at = now() WHERE event_id = ? AND published_at IS NULL",
                        event.id());
                meters.counter("contabilidad.outbox.published").increment();
            } catch (Exception exception) {
                log.warn("Evento pendiente eventId={} intento={}", event.id(), event.attempts());
                meters.counter("contabilidad.outbox.publish_failures").increment();
            }
        }
    }

    private List<Event> claim(int limit) {
        return jdbc.query(
                """
                WITH due AS (
                  SELECT event_id FROM outbox WHERE published_at IS NULL AND next_attempt_at <= now()
                  ORDER BY created_at, event_id LIMIT ? FOR UPDATE SKIP LOCKED
                )
                UPDATE outbox o SET attempts = o.attempts + 1,
                  next_attempt_at = now() + make_interval(secs => LEAST(60, power(2, LEAST(o.attempts + 1, 6))::int))
                FROM due WHERE o.event_id = due.event_id
                RETURNING o.event_id, o.transfer_id, o.payload::text AS payload, o.attempts
                """,
                (rs, row) -> new Event(
                        rs.getObject("event_id", UUID.class),
                        rs.getObject("transfer_id", UUID.class),
                        rs.getString("payload"),
                        rs.getInt("attempts")),
                limit);
    }

    private double pending() {
        Long count = jdbc.queryForObject("SELECT count(*) FROM outbox WHERE published_at IS NULL", Long.class);
        return count == null ? 0 : count;
    }

    private double oldestAgeSeconds() {
        Double age = jdbc.queryForObject(
                "SELECT EXTRACT(EPOCH FROM (now() - min(created_at))) FROM outbox WHERE published_at IS NULL",
                Double.class);
        return age == null ? 0 : age;
    }

    private record Event(UUID id, UUID transferId, String payload, int attempts) {}
}
