package com.logistpulse.lab.outbox;

/** Destino de los eventos del outbox. Lanza una excepción no comprobada si no pudo entregarlo. */
public interface EventSender {
    void send(OutboxEvent event);
}
