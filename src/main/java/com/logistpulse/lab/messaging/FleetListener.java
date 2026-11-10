package com.logistpulse.lab.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/**
 * Simula la flota: al recibir el evento, el robot sale y el pedido pasa a IN_TRANSIT (de forma idempotente).
 * {@code lab.messaging.consumer-enabled=false} deja el consumidor sin arrancar (para observar la cola acumularse).
 */
@Component
@ConditionalOnProperty(name = "lab.messaging.enabled", havingValue = "true")
public class FleetListener {

    private static final Logger log = LoggerFactory.getLogger(FleetListener.class);

    private final DispatchedEventHandler handler;

    public FleetListener(DispatchedEventHandler handler) {
        this.handler = handler;
    }

    @RabbitListener(queues = RabbitTopology.DISPATCHED_QUEUE, autoStartup = "${lab.messaging.consumer-enabled:true}")
    public void onDispatched(String orderId, @Header(name = AmqpHeaders.MESSAGE_ID, required = false) String messageId) {
        log.info("Flota recibió order.dispatched id={} messageId={}", orderId, messageId);
        handler.handle(messageId, Long.valueOf(orderId));
    }
}
