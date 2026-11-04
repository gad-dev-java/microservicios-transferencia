package com.portafolio.banca.contabilidad.domain;

public enum RejectionReason {
    ACCOUNT_NOT_FOUND,
    DESTINATION_ACCOUNT_NOT_FOUND,
    ACCOUNT_INACTIVE,
    CURRENCY_MISMATCH,
    INSUFFICIENT_FUNDS
}
