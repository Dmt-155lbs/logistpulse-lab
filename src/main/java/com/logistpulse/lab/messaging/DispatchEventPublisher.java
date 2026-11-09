package com.logistpulse.lab.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Publica "pedido despachado" DESPUÉS del commit (así el consumidor nunca ve un pedido que aún no existe).
 * Con lab.messaging.enabled=false (modo lite) solo registra el evento en el log.
 *
 * Limitación didáctica: si el proceso cae entre el commit y el envío, el evento se pierde.
 * La solución es el patrón Outbox (ver LAB.md, Parte 7).
 */
@Component
public class DispatchEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(DispatchEventPublisher.class);

    private final boolean enabled;
    private final ObjectProvider<RabbitTemplate> rabbit;

    public DispatchEventPublisher(@Value("${lab.messaging.enabled:false}") boolean enabled,
                                  ObjectProvider<RabbitTemplate> rabbit) {
        this.enabled = enabled;
        this.rabbit = rabbit;
    }

    public void publishAfterCommit(Long orderId) {
        if (!enabled) {
            log.info("[lite] evento order.dispatched id={} (RabbitMQ desactivado)", orderId);
            return;
        }
        Runnable send = () -> {
            rabbit.getObject().convertAndSend(RabbitTopology.DISPATCHED_QUEUE, String.valueOf(orderId));
            log.info("Evento order.dispatched publicado id={}", orderId);
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send.run();
                }
            });
        } else {
            send.run();
        }
    }
}
