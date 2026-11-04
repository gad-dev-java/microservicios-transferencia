package com.portafolio.banca.transferencias.application;

import com.portafolio.banca.transferencias.domain.Transfer;

public interface LedgerPort {
    LedgerResult transfer(Transfer transfer);

    record LedgerResult(String outcome, String reasonCode) {}
}
