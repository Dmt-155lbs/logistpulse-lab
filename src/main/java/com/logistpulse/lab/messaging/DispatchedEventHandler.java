package com.logistpulse.lab.messaging;

import com.logistpulse.lab.api.ApiException;
import com.logistpulse.lab.domain.OrderStatus;
import com.logistpulse.lab.service.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Lógica idempotente del consumidor «la flota recibe order.dispatched».
 *
 * Defensa 1: tabla processed_message con el id del evento (messageId) como clave. La comprobación, la transición y el
 * registro ocurren en UNA transacción: o se hace todo o nada, y dos entregas concurrentes del mismo mensaje chocan en
 * la clave primaria (la perdedora se revierte y Rabbit la reentrega; entonces ve el registro y se descarta).
 * Defensa 2: la máquina de estados. Un mensaje sin id, o un duplicado tardío cuyo registro ya se purgó, intenta una
 * transición ilegal (409): es un error de negocio no recuperable, así que se descarta con un aviso en vez de lanzar la
 * excepción (Spring AMQP reencolaría el mensaje para siempre: bucle de mensaje venenoso).
 */
@Component
public class DispatchedEventHandler {

    private static final Logger log = LoggerFactory.getLogger(DispatchedEventHandler.class);

    public enum Outcome { PROCESSED, DUPLICATE, DISCARDED }

    private final OrderService orders;
    private final ProcessedMessageRepository processed;
    private final TransactionTemplate tx;

    @Autowired
    public DispatchedEventHandler(OrderService orders, ProcessedMessageRepository processed, PlatformTransactionManager tm) {
        this(orders, processed, new TransactionTemplate(tm));
    }

    DispatchedEventHandler(OrderService orders, ProcessedMessageRepository processed, TransactionTemplate tx) {
        this.orders = orders;
        this.processed = processed;
        this.tx = tx;
    }

    public Outcome handle(String messageId, Long orderId) {
        try {
            return tx.execute(status -> {
                if (messageId != null && processed.existsById(messageId)) {
                    log.info("Mensaje {} ya procesado: se ignora el duplicado (pedido {})", messageId, orderId);
                    return Outcome.DUPLICATE;
                }
                orders.transition(orderId, OrderStatus.IN_TRANSIT);
                if (messageId != null) {
                    processed.saveAndFlush(new ProcessedMessage(messageId));
                }
                return Outcome.PROCESSED;
            });
        } catch (ApiException e) {
            log.warn("Mensaje {} descartado (pedido {}): {}", messageId, orderId, e.getMessage());
            return Outcome.DISCARDED;
        }
    }
}
