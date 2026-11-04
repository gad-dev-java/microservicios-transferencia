package com.portafolio.banca.contabilidad.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TransferRulesTest {
    private static final UUID SOURCE_ID = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa1");
    private static final UUID DESTINATION_ID = UUID.fromString("cccccccc-cccc-4ccc-8ccc-ccccccccccc1");

    @Test
    void acceptsTwoActiveAccountsWithFunds() {
        var source = account(SOURCE_ID, "owner", "ACTIVA", "200.00");
        var destination = account(DESTINATION_ID, "other", "ACTIVA", "100.00");

        assertThat(TransferRules.rejectionReason(Optional.of(source), Optional.of(destination), instruction("100.00")))
                .isEmpty();
    }

    @Test
    void rejectsForeignSourceBeforeCheckingBalance() {
        var source = account(SOURCE_ID, "other", "ACTIVA", "0.00");
        var destination = account(DESTINATION_ID, "other", "ACTIVA", "100.00");

        assertThat(TransferRules.rejectionReason(Optional.of(source), Optional.of(destination), instruction("100.00")))
                .contains(RejectionReason.ACCOUNT_NOT_FOUND);
    }

    @Test
    void rejectsInactiveDestinationWithoutMovement() {
        var source = account(SOURCE_ID, "owner", "ACTIVA", "200.00");
        var destination = account(DESTINATION_ID, "other", "INACTIVA", "100.00");

        assertThat(TransferRules.rejectionReason(Optional.of(source), Optional.of(destination), instruction("100.00")))
                .contains(RejectionReason.ACCOUNT_INACTIVE);
    }

    @Test
    void instructionRequiresTwoDecimalPlaces() {
        assertThatThrownBy(() -> instruction("100.000")).isInstanceOf(IllegalArgumentException.class);
    }

    private static Account account(UUID id, String owner, String status, String balance) {
        return new Account(id, owner, "PEN", status, new BigDecimal(balance));
    }

    private static TransferInstruction instruction(String amount) {
        return new TransferInstruction(
                UUID.randomUUID(), "owner", SOURCE_ID, DESTINATION_ID, new BigDecimal(amount), "PEN");
    }
}
