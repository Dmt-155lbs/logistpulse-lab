package com.logistpulse.lab.outbox;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Registra eventos en la tabla outbox. Exige una transacción en curso: ese es todo el sentido del patrón. */
@Service
public class OutboxService {

    public static final String ORDER_DISPATCHED = "order.dispatched";

    private final OutboxRepository repo;

    public OutboxService(OutboxRepository repo) {
        this.repo = repo;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public OutboxEvent record(String type, String payload) {
        return repo.save(new OutboxEvent(type, payload));
    }
}
