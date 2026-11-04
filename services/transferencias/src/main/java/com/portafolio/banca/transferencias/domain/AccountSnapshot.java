package com.portafolio.banca.transferencias.domain;

import java.math.BigDecimal;
import java.util.UUID;

public record AccountSnapshot(UUID id, String ownerSubject, String currency, String status, BigDecimal balance) {}
