package com.portafolio.banca.historial.infrastructure.kafka;

import com.portafolio.banca.historial.application.HistoryService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
public class TransferConfirmedConsumer {
    private final HistoryService history;

    public TransferConfirmedConsumer(HistoryService history) {
        this.history = history;
    }

    @KafkaListener(topics = "transferencias.confirmadas.v1")
    public void receive(String payload, Acknowledgment acknowledgment) {
        history.receive(payload);
        acknowledgment.acknowledge();
    }
}
