package com.portafolio.banca.contabilidad.application;

import com.portafolio.banca.contabilidad.domain.Account;
import com.portafolio.banca.contabilidad.domain.TransferInstruction;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountingStore {
    Optional<Account> account(UUID id);

    List<Account> accounts(String ownerSubject);

    Optional<Account> lockAccount(UUID id);

    Optional<TransferResult> result(UUID transferId);

    boolean beginCommand(UUID transferId, String requestHash);

    Optional<String> requestHash(UUID transferId);

    void reject(UUID transferId, String reason);

    void confirm(TransferInstruction instruction, UUID movementId);
}
