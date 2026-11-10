package com.logistpulse.lab.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Modo lite (lab.messaging.enabled=false): no hay broker, el evento solo se escribe en el log. */
@Component
@ConditionalOnProperty(name = "lab.messaging.enabled", havingValue = "false", matchIfMissing = true)
public class LogEventSender implements EventSender {

    private static final Logger log = LoggerFactory.getLogger(LogEventSender.class);

    @Override
    public void send(OutboxEvent event) {
        log.info("[lite] evento {} outboxId={} payload={} (RabbitMQ desactivado)",
                event.getType(), event.getId(), event.getPayload());
    }
}
