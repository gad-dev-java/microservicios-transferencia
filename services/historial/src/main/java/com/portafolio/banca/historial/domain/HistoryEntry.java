package com.portafolio.banca.historial.domain;

import java.time.Instant;
import java.util.UUID;

public record HistoryEntry(
        UUID eventId,
        UUID transferenciaId,
        String titularSubject,
        UUID cuentaOrigenId,
        UUID cuentaDestinoId,
        String importe,
        String moneda,
        Instant ocurridaEn,
        String eventHash) {}
