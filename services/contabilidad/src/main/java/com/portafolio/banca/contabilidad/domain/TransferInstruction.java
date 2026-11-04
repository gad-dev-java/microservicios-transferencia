package com.portafolio.banca.contabilidad.domain;

import java.math.BigDecimal;
import java.util.UUID;

public record TransferInstruction(
        UUID transferId,
        String actorSubject,
        UUID sourceAccountId,
        UUID destinationAccountId,
        BigDecimal amount,
        String currency) {
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("99999999999999999.99");

    public TransferInstruction {
        if (transferId == null
                || actorSubject == null
                || actorSubject.isBlank()
                || actorSubject.length() > 128
                || sourceAccountId == null
                || destinationAccountId == null
                || sourceAccountId.equals(destinationAccountId)
                || amount == null
                || amount.scale() != 2
                || amount.signum() <= 0
                || amount.compareTo(MAX_AMOUNT) > 0
                || !"PEN".equals(currency)) {
            throw new IllegalArgumentException("Comando de transferencia inválido");
        }
    }
}
