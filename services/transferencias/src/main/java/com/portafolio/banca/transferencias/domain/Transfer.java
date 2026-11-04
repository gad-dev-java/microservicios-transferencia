package com.portafolio.banca.transferencias.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record Transfer(
        UUID id,
        String ownerSubject,
        UUID idempotencyKey,
        String requestHash,
        UUID sourceAccountId,
        UUID destinationAccountId,
        BigDecimal amount,
        String currency,
        String state,
        String reasonCode,
        int attempts,
        Instant createdAt,
        Instant updatedAt) {}
