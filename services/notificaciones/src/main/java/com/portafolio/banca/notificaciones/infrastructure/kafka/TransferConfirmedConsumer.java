package com.portafolio.banca.notificaciones.infrastructure.kafka;

import com.portafolio.banca.notificaciones.application.NotificationService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
public class TransferConfirmedConsumer {
    private final NotificationService notifications;

    public TransferConfirmedConsumer(NotificationService notifications) {
        this.notifications = notifications;
    }

    @KafkaListener(topics = "transferencias.confirmadas.v1")
    public void receive(String payload, Acknowledgment acknowledgment) {
        notifications.receive(payload);
        acknowledgment.acknowledge();
    }
}
