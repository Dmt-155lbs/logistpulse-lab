package com.logistpulse.lab.messaging;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.time.Instant;

/** Registro de mensajes ya consumidos: la clave primaria (id del mensaje) es lo que impide procesarlo dos veces. */
@Entity
public class ProcessedMessage {

    @Id
    @Column(length = 100)
    private String messageId;

    @Column(nullable = false)
    private Instant processedAt = Instant.now();

    protected ProcessedMessage() {
    }

    public ProcessedMessage(String messageId) {
        this.messageId = messageId;
    }

    public String getMessageId() { return messageId; }
    public Instant getProcessedAt() { return processedAt; }
}
