package com.portafolio.banca.contabilidad.api;

import com.portafolio.banca.contabilidad.domain.Account;
import java.math.BigDecimal;
import java.util.UUID;

public record AccountResponse(UUID accountId, String ownerSubject, String currency, String status, BigDecimal balance) {
    static AccountResponse from(Account account) {
        return new AccountResponse(
                account.accountId(), account.ownerSubject(), account.currency(), account.status(), account.balance());
    }
}
