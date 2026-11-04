package com.portafolio.banca.contabilidad.application;

import com.portafolio.banca.contabilidad.domain.Account;
import com.portafolio.banca.contabilidad.domain.TransferInstruction;
import com.portafolio.banca.contabilidad.domain.TransferRules;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class AccountingService {
    private static final Logger log = LoggerFactory.getLogger(AccountingService.class);
    private final AccountingStore store;
    private final MeterRegistry meters;

    public AccountingService(AccountingStore store, MeterRegistry meters) {
        this.store = store;
        this.meters = meters;
    }

    @Transactional(readOnly = true)
    public Account account(UUID id) {
        return store.account(id).orElseThrow(() -> new NotFoundException("Cuenta inexistente"));
    }

    @Transactional(readOnly = true)
    public TransferResult result(UUID transferId) {
        return store.result(transferId).orElseThrow(() -> new NotFoundException("Transferencia inexistente"));
    }

    @Transactional
    public TransferResult transfer(TransferInstruction instruction) {
        String fingerprint = TransferFingerprint.from(instruction);
        if (!store.beginCommand(instruction.transferId(), fingerprint)) {
            String existing = store.requestHash(instruction.transferId())
                    .orElseThrow(() -> new IllegalStateException("Resultado idempotente inexistente"));
            if (!fingerprint.equals(existing)) {
                throw new IdempotencyConflictException();
            }
            return result(instruction.transferId());
        }

        UUID first = instruction
                                .sourceAccountId()
                                .toString()
                                .compareTo(instruction.destinationAccountId().toString())
                        < 0
                ? instruction.sourceAccountId()
                : instruction.destinationAccountId();
        UUID second = first.equals(instruction.sourceAccountId())
                ? instruction.destinationAccountId()
                : instruction.sourceAccountId();
        Optional<Account> firstAccount = store.lockAccount(first);
        Optional<Account> secondAccount = store.lockAccount(second);
        Optional<Account> source = first.equals(instruction.sourceAccountId()) ? firstAccount : secondAccount;
        Optional<Account> destination = first.equals(instruction.destinationAccountId()) ? firstAccount : secondAccount;
        var rejection = TransferRules.rejectionReason(source, destination, instruction);
        if (rejection.isPresent()) {
            String reason = rejection.get().name();
            store.reject(instruction.transferId(), reason);
            recordAfterCommit(instruction.transferId(), "RECHAZADA", reason);
            return new TransferResult(instruction.transferId(), "RECHAZADA", reason, null);
        }

        UUID movementId = UUID.randomUUID();
        store.confirm(instruction, movementId);
        recordAfterCommit(instruction.transferId(), "CONFIRMADA", "NONE");
        return new TransferResult(instruction.transferId(), "CONFIRMADA", null, movementId);
    }

    private void recordAfterCommit(UUID transferId, String outcome, String reason) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                meters.counter("contabilidad.transferencias", "outcome", outcome, "reason", reason)
                        .increment();
                log.info("Resultado contable transferId={} outcome={} reason={}", transferId, outcome, reason);
            }
        });
    }
}
