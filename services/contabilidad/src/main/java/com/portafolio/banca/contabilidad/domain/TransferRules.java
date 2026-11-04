package com.portafolio.banca.contabilidad.domain;

import java.util.Optional;

public final class TransferRules {
    private TransferRules() {}

    public static Optional<RejectionReason> rejectionReason(
            Optional<Account> source, Optional<Account> destination, TransferInstruction instruction) {
        if (source.isEmpty() || !instruction.actorSubject().equals(source.get().ownerSubject())) {
            return Optional.of(RejectionReason.ACCOUNT_NOT_FOUND);
        }
        if (destination.isEmpty()) {
            return Optional.of(RejectionReason.DESTINATION_ACCOUNT_NOT_FOUND);
        }
        if (!"ACTIVA".equals(source.get().status())
                || !"ACTIVA".equals(destination.get().status())) {
            return Optional.of(RejectionReason.ACCOUNT_INACTIVE);
        }
        if (!"PEN".equals(source.get().currency())
                || !"PEN".equals(destination.get().currency())) {
            return Optional.of(RejectionReason.CURRENCY_MISMATCH);
        }
        if (source.get().balance().compareTo(instruction.amount()) < 0) {
            return Optional.of(RejectionReason.INSUFFICIENT_FUNDS);
        }
        return Optional.empty();
    }
}
