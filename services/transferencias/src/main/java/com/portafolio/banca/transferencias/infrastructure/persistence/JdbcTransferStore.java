package com.portafolio.banca.transferencias.infrastructure.persistence;

import com.portafolio.banca.transferencias.application.TransferStore;
import com.portafolio.banca.transferencias.domain.Transfer;
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

    public JdbcTransferStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
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
