package com.portafolio.banca.contabilidad.infrastructure.persistence;

import com.portafolio.banca.contabilidad.application.AccountingStore;
import com.portafolio.banca.contabilidad.application.TransferResult;
import com.portafolio.banca.contabilidad.domain.Account;
import com.portafolio.banca.contabilidad.domain.TransferInstruction;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcAccountingStore implements AccountingStore {
    private final JdbcTemplate jdbc;

    public JdbcAccountingStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Account> account(UUID id) {
        List<Account> rows = jdbc.query("""
                SELECT id, owner_subject, currency, status, balance FROM cuentas WHERE id = ?
                """, (rs, row) -> mapAccount(rs), id);
        return rows.stream().findFirst();
    }

    @Override
    public Optional<Account> lockAccount(UUID id) {
        List<Account> rows = jdbc.query("""
                SELECT id, owner_subject, currency, status, balance FROM cuentas WHERE id = ? FOR UPDATE
                """, (rs, row) -> mapAccount(rs), id);
        return rows.stream().findFirst();
    }

    @Override
    public Optional<TransferResult> result(UUID transferId) {
        List<TransferResult> rows = jdbc.query("""
                SELECT transfer_id, outcome, reason_code, movement_id FROM comandos WHERE transfer_id = ?
                """, (rs, row) -> mapResult(rs), transferId);
        return rows.stream().findFirst();
    }

    @Override
    public boolean beginCommand(UUID transferId, String requestHash) {
        int inserted = jdbc.update("""
                INSERT INTO comandos (transfer_id, request_hash, outcome)
                VALUES (?, ?, 'PROCESSING') ON CONFLICT DO NOTHING
                """, transferId, requestHash);
        return inserted == 1;
    }

    @Override
    public Optional<String> requestHash(UUID transferId) {
        List<String> rows = jdbc.query(
                "SELECT request_hash FROM comandos WHERE transfer_id = ?", (rs, row) -> rs.getString(1), transferId);
        return rows.stream().findFirst();
    }

    @Override
    public void reject(UUID transferId, String reason) {
        jdbc.update("""
                UPDATE comandos SET outcome = 'RECHAZADA', reason_code = ?, updated_at = now()
                WHERE transfer_id = ?
                """, reason, transferId);
    }

    @Override
    public void confirm(TransferInstruction instruction, UUID movementId) {
        jdbc.update(
                "UPDATE cuentas SET balance = balance - ? WHERE id = ?",
                instruction.amount(),
                instruction.sourceAccountId());
        jdbc.update(
                "UPDATE cuentas SET balance = balance + ? WHERE id = ?",
                instruction.amount(),
                instruction.destinationAccountId());
        jdbc.update(
                "INSERT INTO movimientos (id, transfer_id, actor_subject) VALUES (?, ?, ?)",
                movementId,
                instruction.transferId(),
                instruction.actorSubject());
        jdbc.update(
                "INSERT INTO asientos (id, movement_id, account_id, delta) VALUES (?, ?, ?, ?)",
                UUID.randomUUID(),
                movementId,
                instruction.sourceAccountId(),
                instruction.amount().negate());
        jdbc.update(
                "INSERT INTO asientos (id, movement_id, account_id, delta) VALUES (?, ?, ?, ?)",
                UUID.randomUUID(),
                movementId,
                instruction.destinationAccountId(),
                instruction.amount());
        jdbc.update("""
                UPDATE comandos SET outcome = 'CONFIRMADA', movement_id = ?, updated_at = now()
                WHERE transfer_id = ?
                """, movementId, instruction.transferId());
    }

    private Account mapAccount(ResultSet rs) throws SQLException {
        return new Account(
                rs.getObject("id", UUID.class),
                rs.getString("owner_subject"),
                rs.getString("currency"),
                rs.getString("status"),
                rs.getBigDecimal("balance"));
    }

    private TransferResult mapResult(ResultSet rs) throws SQLException {
        return new TransferResult(
                rs.getObject("transfer_id", UUID.class),
                rs.getString("outcome"),
                rs.getString("reason_code"),
                rs.getObject("movement_id", UUID.class));
    }
}
