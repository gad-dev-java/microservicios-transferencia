package com.portafolio.banca.transferencias.api;

import com.portafolio.banca.transferencias.domain.AccountSnapshot;
import java.util.UUID;

public record CuentaResponse(UUID id, String moneda, String saldoDisponible, String estado) {
    static CuentaResponse from(AccountSnapshot account) {
        return new CuentaResponse(
                account.id(), account.currency(), account.balance().toPlainString(), account.status());
    }
}
