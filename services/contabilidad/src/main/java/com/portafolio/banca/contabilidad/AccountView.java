package com.portafolio.banca.contabilidad;

import java.math.BigDecimal;
import java.util.UUID;

public record AccountView(UUID accountId, String ownerSubject, String currency, String status, BigDecimal balance) {
}
