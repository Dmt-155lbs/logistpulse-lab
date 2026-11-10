package com.logistpulse.lab.outbox;

import com.logistpulse.lab.messaging.RabbitTopology;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Publica en RabbitMQ y espera la confirmación del broker (publisher confirm): solo así «enviado» significa que el
 * broker aceptó el mensaje. El id del outbox viaja como messageId para que el consumidor pueda deduplicar.
 */
@Component
@ConditionalOnProperty(name = "lab.messaging.enabled", havingValue = "true")
public class RabbitEventSender implements EventSender {

    private static final Logger log = LoggerFactory.getLogger(RabbitEventSender.class);
    private static final long CONFIRM_TIMEOUT_MS = 5_000;

    private final RabbitTemplate rabbit;

    public RabbitEventSender(RabbitTemplate rabbit) {
        this.rabbit = rabbit;
    }

    @Override
    public void send(OutboxEvent event) {
        rabbit.invoke(ops -> {
            ops.convertAndSend(RabbitTopology.DISPATCHED_QUEUE, (Object) event.getPayload(), m -> {
                MessageProperties p = m.getMessageProperties();
                p.setMessageId(String.valueOf(event.getId()));
                p.setType(event.getType());
                return m;
            });
            ops.waitForConfirmsOrDie(CONFIRM_TIMEOUT_MS);
            return null;
        });
        log.info("Evento {} publicado id={} outboxId={}", event.getType(), event.getPayload(), event.getId());
    }
}
