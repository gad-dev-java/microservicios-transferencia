package com.portafolio.banca.contabilidad;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class AccountingService {
    private static final Logger log = LoggerFactory.getLogger(AccountingService.class);
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("99999999999999999.99");
    private final JdbcTemplate jdbc;
    private final MeterRegistry meters;

    public AccountingService(JdbcTemplate jdbc, MeterRegistry meters) {
        this.jdbc = jdbc;
        this.meters = meters;
    }

    @Transactional(readOnly = true)
    public AccountView account(UUID id) {
        List<AccountView> accounts = jdbc.query("""
                SELECT id, owner_subject, currency, status, balance FROM cuentas WHERE id = ?
                """, (rs, row) -> mapAccount(rs), id);
        if (accounts.isEmpty()) {
            throw new NotFoundException("Cuenta inexistente");
        }
        return accounts.getFirst();
    }

    @Transactional(readOnly = true)
    public TransferResult result(UUID transferId) {
        List<TransferResult> results = jdbc.query("""
                SELECT transfer_id, outcome, reason_code, movement_id FROM comandos WHERE transfer_id = ?
                """, (rs, row) -> mapResult(rs), transferId);
        if (results.isEmpty()) {
            throw new NotFoundException("Transferencia inexistente");
        }
        return results.getFirst();
    }

    @Transactional
    public TransferResult transfer(TransferCommand command) {
        BigDecimal amount = validate(command);
        String hash = hash(command, amount);
        int inserted = jdbc.update("""
                INSERT INTO comandos (transfer_id, request_hash, outcome)
                VALUES (?, ?, 'PROCESSING') ON CONFLICT DO NOTHING
                """, command.transferId(), hash);
        if (inserted == 0) {
            String existingHash = jdbc.queryForObject(
                    "SELECT request_hash FROM comandos WHERE transfer_id = ?", String.class, command.transferId());
            if (!hash.equals(existingHash)) {
                throw new IdempotencyConflictException();
            }
            return result(command.transferId());
        }

        UUID first = command.sourceAccountId().toString().compareTo(command.destinationAccountId().toString()) < 0
                ? command.sourceAccountId() : command.destinationAccountId();
        UUID second = first.equals(command.sourceAccountId())
                ? command.destinationAccountId() : command.sourceAccountId();
        AccountView firstAccount = lockedAccount(first);
        AccountView secondAccount = lockedAccount(second);
        AccountView source = first.equals(command.sourceAccountId()) ? firstAccount : secondAccount;
        AccountView destination = first.equals(command.destinationAccountId()) ? firstAccount : secondAccount;

        if (source == null || !command.actorSubject().equals(source.ownerSubject())) {
            return reject(command.transferId(), "ACCOUNT_NOT_FOUND");
        }
        if (destination == null) {
            return reject(command.transferId(), "DESTINATION_ACCOUNT_NOT_FOUND");
        }
        if (!"ACTIVA".equals(source.status()) || !"ACTIVA".equals(destination.status())) {
            return reject(command.transferId(), "ACCOUNT_INACTIVE");
        }
        if (!"PEN".equals(source.currency()) || !"PEN".equals(destination.currency())) {
            return reject(command.transferId(), "CURRENCY_MISMATCH");
        }
        if (source.balance().compareTo(amount) < 0) {
            return reject(command.transferId(), "INSUFFICIENT_FUNDS");
        }

        UUID movementId = UUID.randomUUID();
        jdbc.update("UPDATE cuentas SET balance = balance - ? WHERE id = ?", amount, source.accountId());
        jdbc.update("UPDATE cuentas SET balance = balance + ? WHERE id = ?", amount, destination.accountId());
        jdbc.update("INSERT INTO movimientos (id, transfer_id, actor_subject) VALUES (?, ?, ?)",
                movementId, command.transferId(), command.actorSubject());
        jdbc.update("INSERT INTO asientos (id, movement_id, account_id, delta) VALUES (?, ?, ?, ?)",
                UUID.randomUUID(), movementId, source.accountId(), amount.negate());
        jdbc.update("INSERT INTO asientos (id, movement_id, account_id, delta) VALUES (?, ?, ?, ?)",
                UUID.randomUUID(), movementId, destination.accountId(), amount);
        jdbc.update("""
                UPDATE comandos SET outcome = 'CONFIRMADA', movement_id = ?, updated_at = now()
                WHERE transfer_id = ?
                """, movementId, command.transferId());
        recordAfterCommit(command.transferId(), "CONFIRMADA", "NONE");
        return new TransferResult(command.transferId(), "CONFIRMADA", null, movementId);
    }

    private AccountView lockedAccount(UUID id) {
        List<AccountView> accounts = jdbc.query("""
                SELECT id, owner_subject, currency, status, balance FROM cuentas WHERE id = ? FOR UPDATE
                """, (rs, row) -> mapAccount(rs), id);
        return accounts.isEmpty() ? null : accounts.getFirst();
    }

    private TransferResult reject(UUID transferId, String reason) {
        jdbc.update("""
                UPDATE comandos SET outcome = 'RECHAZADA', reason_code = ?, updated_at = now()
                WHERE transfer_id = ?
                """, reason, transferId);
        recordAfterCommit(transferId, "RECHAZADA", reason);
        return new TransferResult(transferId, "RECHAZADA", reason, null);
    }

    private void recordAfterCommit(UUID transferId, String outcome, String reason) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                meters.counter("contabilidad.transferencias", "outcome", outcome, "reason", reason).increment();
                log.info("Resultado contable transferId={} outcome={} reason={}", transferId, outcome, reason);
            }
        });
    }

    private static BigDecimal validate(TransferCommand command) {
        if (command == null || command.transferId() == null || command.sourceAccountId() == null
                || command.destinationAccountId() == null || command.actorSubject() == null
                || command.actorSubject().isBlank() || command.actorSubject().length() > 128
                || command.amount() == null || !command.amount().matches("[0-9]+\\.[0-9]{2}")
                || !"PEN".equals(command.currency())
                || command.sourceAccountId().equals(command.destinationAccountId())) {
            throw new IllegalArgumentException("Comando de transferencia inválido");
        }
        BigDecimal amount = new BigDecimal(command.amount());
        if (amount.signum() <= 0 || amount.compareTo(MAX_AMOUNT) > 0) {
            throw new IllegalArgumentException("Importe fuera del rango permitido");
        }
        return amount;
    }

    private static String hash(TransferCommand command, BigDecimal amount) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String value : List.of(command.actorSubject(), command.sourceAccountId().toString(),
                    command.destinationAccountId().toString(), amount.toPlainString(), command.currency())) {
                byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
                digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
                digest.update(bytes);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }

    private static AccountView mapAccount(ResultSet rs) throws SQLException {
        return new AccountView(rs.getObject("id", UUID.class), rs.getString("owner_subject"),
                rs.getString("currency"), rs.getString("status"), rs.getBigDecimal("balance"));
    }

    private static TransferResult mapResult(ResultSet rs) throws SQLException {
        return new TransferResult(rs.getObject("transfer_id", UUID.class), rs.getString("outcome"),
                rs.getString("reason_code"), rs.getObject("movement_id", UUID.class));
    }
}
