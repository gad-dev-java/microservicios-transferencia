package com.portafolio.banca.riesgo.api;

import java.util.UUID;

public record EvaluationRequest(
        UUID transferId, UUID sourceAccountId, UUID destinationAccountId, String amount, String currency) {}
