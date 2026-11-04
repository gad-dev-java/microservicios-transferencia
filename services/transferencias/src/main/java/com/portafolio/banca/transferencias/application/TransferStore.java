package com.portafolio.banca.transferencias.application;

import com.portafolio.banca.transferencias.domain.Transfer;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TransferStore {
    Optional<Transfer> byKey(String owner, UUID key);

    Optional<Transfer> byId(UUID id);

    boolean insert(Transfer transfer);

    void state(UUID id, String expected, String next, String reason);

    List<Transfer> page(String owner, Instant beforeTime, UUID beforeId, int limit);

    List<Transfer> claimForRecovery(int limit);

    void recoveryFailure(UUID id, String previousState, String errorCode);
}
