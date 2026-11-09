package com.logistpulse.lab.messaging;

import org.springframework.amqp.core.Queue;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "lab.messaging.enabled", havingValue = "true")
public class RabbitTopology {

    public static final String DISPATCHED_QUEUE = "orders.dispatched";

    @Bean
    Queue dispatchedQueue() {
        return new Queue(DISPATCHED_QUEUE, true);
    }
}
