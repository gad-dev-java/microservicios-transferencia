package com.portafolio.banca.contabilidad;

import java.util.UUID;

public record TransferResult(UUID transferId, String outcome, String reasonCode, UUID movementId) {
}
