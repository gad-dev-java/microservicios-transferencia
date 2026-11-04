package com.portafolio.banca.transferencias.infrastructure.persistence;

import com.portafolio.banca.transferencias.application.TransferStore;
import com.portafolio.banca.transferencias.domain.Transfer;
import io.micrometer.core.instrument.MeterRegistry;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcTransferStore implements TransferStore {
    private static final RowMapper<Transfer> MAPPER = (rs, row) -> from(rs);
    private final JdbcTemplate jdbc;

    public JdbcTransferStore(JdbcTemplate jdbc, MeterRegistry meters) {
        this.jdbc = jdbc;
        meters.gauge("transferencias.conciliacion.pendientes", this, store -> store.pending());
    }

    @Override
    public Optional<Transfer> byKey(String owner, UUID key) {
        return jdbc
                .query(
                        "SELECT * FROM transferencias WHERE owner_subject = ? AND idempotency_key = ?",
                        MAPPER,
                        owner,
                        key)
                .stream()
                .findFirst();
    }

    @Override
    public Optional<Transfer> byId(UUID id) {
        return jdbc.query("SELECT * FROM transferencias WHERE id = ?", MAPPER, id).stream()
                .findFirst();
    }

    @Override
    public boolean insert(Transfer transfer) {
        return jdbc.update(
                        """
                        INSERT INTO transferencias (id, owner_subject, idempotency_key, request_hash,
                          source_account_id, destination_account_id, amount, currency, state)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'RECIBIDA')
                        ON CONFLICT (owner_subject, idempotency_key) DO NOTHING
                        """,
                        transfer.id(),
                        transfer.ownerSubject(),
                        transfer.idempotencyKey(),
                        transfer.requestHash(),
                        transfer.sourceAccountId(),
                        transfer.destinationAccountId(),
                        transfer.amount(),
                        transfer.currency())
                == 1;
    }

    @Override
    public void state(UUID id, String expected, String next, String reason) {
        jdbc.update("""
                UPDATE transferencias SET state = ?, reason_code = ?, updated_at = now(),
                    next_attempt_at = CASE WHEN ? = 'PENDIENTE_CONCILIACION' THEN now() + interval '2 seconds' ELSE NULL END
                WHERE id = ? AND state = ?
                """, next, reason, next, id, expected);
    }

    @Override
    public List<Transfer> page(String owner, Instant beforeTime, UUID beforeId, int limit) {
        if (beforeTime == null) {
            return jdbc.query(
                    "SELECT * FROM transferencias WHERE owner_subject = ? ORDER BY created_at DESC, id DESC LIMIT ?",
                    MAPPER,
                    owner,
                    limit);
        }
        return jdbc.query("""
                SELECT * FROM transferencias WHERE owner_subject = ? AND (created_at, id) < (?, ?)
                ORDER BY created_at DESC, id DESC LIMIT ?
                """, MAPPER, owner, Timestamp.from(beforeTime), beforeId, limit);
    }

    @Override
    public List<Transfer> claimForRecovery(int limit) {
        return jdbc.query("""
                WITH due AS (
                    SELECT id FROM transferencias
                    WHERE state IN ('RECIBIDA','EVALUANDO','CONTABILIZANDO','PENDIENTE_CONCILIACION')
                      AND attempts < 10
                      AND (next_attempt_at <= now()
                           OR (next_attempt_at IS NULL AND updated_at < now() - interval '5 seconds'))
                    ORDER BY created_at, id
                    LIMIT ? FOR UPDATE SKIP LOCKED
                )
                UPDATE transferencias t
                SET attempts = t.attempts + 1,
                    next_attempt_at = now() + make_interval(secs => LEAST(60, power(2, LEAST(t.attempts + 1, 6))::int))
                FROM due WHERE t.id = due.id RETURNING t.*
                """, MAPPER, limit);
    }

    @Override
    public void recoveryFailure(UUID id, String previousState, String errorCode) {
        jdbc.update("""
                UPDATE transferencias SET state = 'PENDIENTE_CONCILIACION',
                  last_error_code = ?, updated_at = now()
                WHERE id = ? AND state = ?
                """, errorCode, id, previousState);
    }

    private double pending() {
        Long count = jdbc.queryForObject(
                "SELECT count(*) FROM transferencias WHERE state IN ('CONTABILIZANDO','PENDIENTE_CONCILIACION')",
                Long.class);
        return count == null ? 0 : count;
    }

    private static Transfer from(ResultSet rs) throws SQLException {
        return new Transfer(
                rs.getObject("id", UUID.class),
                rs.getString("owner_subject"),
                rs.getObject("idempotency_key", UUID.class),
                rs.getString("request_hash"),
                rs.getObject("source_account_id", UUID.class),
                rs.getObject("destination_account_id", UUID.class),
                rs.getBigDecimal("amount"),
                rs.getString("currency"),
                rs.getString("state"),
                rs.getString("reason_code"),
                rs.getInt("attempts"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant());
    }
}
