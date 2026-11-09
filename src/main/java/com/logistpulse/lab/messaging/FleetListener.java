package com.logistpulse.lab.messaging;

import com.logistpulse.lab.domain.OrderStatus;
import com.logistpulse.lab.service.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Simula la flota: al recibir el evento, el robot sale y el pedido pasa a IN_TRANSIT. */
@Component
@ConditionalOnProperty(name = "lab.messaging.enabled", havingValue = "true")
public class FleetListener {

    private static final Logger log = LoggerFactory.getLogger(FleetListener.class);

    private final OrderService orders;

    public FleetListener(OrderService orders) {
        this.orders = orders;
    }

    @RabbitListener(queues = RabbitTopology.DISPATCHED_QUEUE)
    public void onDispatched(String orderId) {
        log.info("Flota recibió order.dispatched id={}", orderId);
        orders.transition(Long.valueOf(orderId), OrderStatus.IN_TRANSIT);
    }
}
