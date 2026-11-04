package com.portafolio.banca.historial.api;

import com.portafolio.banca.historial.domain.HistoryEntry;
import java.time.Instant;
import java.util.UUID;

public record HistoryResponse(
        UUID eventId,
        UUID transferenciaId,
        UUID cuentaOrigenId,
        UUID cuentaDestinoId,
        String importe,
        String moneda,
        Instant ocurridaEn) {
    static HistoryResponse from(HistoryEntry entry) {
        return new HistoryResponse(
                entry.eventId(),
                entry.transferenciaId(),
                entry.cuentaOrigenId(),
                entry.cuentaDestinoId(),
                entry.importe(),
                entry.moneda(),
                entry.ocurridaEn());
    }
}
