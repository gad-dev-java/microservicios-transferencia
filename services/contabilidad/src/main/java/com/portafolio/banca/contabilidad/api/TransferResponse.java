package com.portafolio.banca.contabilidad.api;

import com.portafolio.banca.contabilidad.application.TransferResult;
import java.util.UUID;

public record TransferResponse(UUID transferId, String outcome, String reasonCode, UUID movementId) {
    static TransferResponse from(TransferResult result) {
        return new TransferResponse(result.transferId(), result.outcome(), result.reasonCode(), result.movementId());
    }
}
