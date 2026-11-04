package com.portafolio.banca.contabilidad.domain;

import java.math.BigDecimal;
import java.util.UUID;

public record Account(UUID accountId, String ownerSubject, String currency, String status, BigDecimal balance) {}
