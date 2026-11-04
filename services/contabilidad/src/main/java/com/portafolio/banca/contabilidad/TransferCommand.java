package com.portafolio.banca.contabilidad;

import java.util.UUID;

public record TransferCommand(
        UUID transferId,
        String actorSubject,
        UUID sourceAccountId,
        UUID destinationAccountId,
        String amount,
        String currency) {}
