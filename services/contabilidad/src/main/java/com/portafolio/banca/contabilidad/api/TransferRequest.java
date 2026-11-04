package com.portafolio.banca.contabilidad.api;

import com.portafolio.banca.contabilidad.domain.TransferInstruction;
import java.math.BigDecimal;
import java.util.UUID;

public record TransferRequest(
        UUID transferId,
        String actorSubject,
        UUID sourceAccountId,
        UUID destinationAccountId,
        String amount,
        String currency) {

    TransferInstruction toInstruction() {
        if (amount == null || !amount.matches("[0-9]+\\.[0-9]{2}")) {
            throw new IllegalArgumentException("Importe inválido");
        }
        return new TransferInstruction(
                transferId, actorSubject, sourceAccountId, destinationAccountId, new BigDecimal(amount), currency);
    }
}
