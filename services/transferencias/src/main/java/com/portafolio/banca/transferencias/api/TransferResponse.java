package com.portafolio.banca.transferencias.api;

import com.portafolio.banca.transferencias.domain.Transfer;
import java.time.Instant;
import java.util.UUID;

public record TransferResponse(
        UUID id,
        UUID cuentaOrigenId,
        UUID cuentaDestinoId,
        String importe,
        String moneda,
        String estado,
        String motivo,
        Instant creadaEn,
        Instant actualizadaEn) {
    static TransferResponse from(Transfer transfer) {
        return new TransferResponse(
                transfer.id(),
                transfer.sourceAccountId(),
                transfer.destinationAccountId(),
                transfer.amount().toPlainString(),
                transfer.currency(),
                transfer.state(),
                transfer.reasonCode(),
                transfer.createdAt(),
                transfer.updatedAt());
    }
}
