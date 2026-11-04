package com.portafolio.banca.transferencias.application;

import com.portafolio.banca.transferencias.domain.Transfer;
import java.util.Optional;
import java.util.UUID;

public interface LedgerPort {
    LedgerResult transfer(Transfer transfer);

    Optional<LedgerResult> result(UUID transferId);

    record LedgerResult(String outcome, String reasonCode) {}
}
