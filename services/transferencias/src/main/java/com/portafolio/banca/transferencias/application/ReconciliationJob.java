package com.portafolio.banca.transferencias.application;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ReconciliationJob {
    private final TransferStore store;
    private final TransferService transfers;

    public ReconciliationJob(TransferStore store, TransferService transfers) {
        this.store = store;
        this.transfers = transfers;
    }

    @Scheduled(fixedDelayString = "${reconciliation.delay-ms:2000}")
    public void reconcile() {
        for (var transfer : store.claimForRecovery(20)) {
            transfers.recover(transfer);
        }
    }
}
