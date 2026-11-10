package com.logistpulse.lab.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Fila de la tabla outbox: un evento que debe publicarse en el broker. Se inserta en la MISMA transacción que el
 * cambio de estado de negocio, de modo que ambos se confirman o ambos se deshacen. El id (IDENTITY, creciente) es
 * a la vez el orden de publicación y el identificador de mensaje que usa el consumidor para deduplicar.
 */
@Entity
@Table(name = "outbox_event", indexes = @Index(name = "idx_outbox_status_id", columnList = "status, id"))
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 60)
    private String type;

    @Column(nullable = false, length = 2000)
    private String payload;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    private Instant publishedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private OutboxStatus status = OutboxStatus.PENDING;

    @Column(nullable = false)
    private int attempts;

    @Column(length = 500)
    private String lastError;

    protected OutboxEvent() {
    }

    public OutboxEvent(String type, String payload) {
        this.type = type;
        this.payload = payload;
    }

    public void markSent(Instant at) {
        this.status = OutboxStatus.SENT;
        this.publishedAt = at;
        this.attempts++;
        this.lastError = null;
    }

    public void markFailed(String error) {
        this.attempts++;
        this.lastError = error == null ? null : error.substring(0, Math.min(error.length(), 500));
    }

    public Long getId() { return id; }
    public String getType() { return type; }
    public String getPayload() { return payload; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getPublishedAt() { return publishedAt; }
    public OutboxStatus getStatus() { return status; }
    public int getAttempts() { return attempts; }
    public String getLastError() { return lastError; }
}
