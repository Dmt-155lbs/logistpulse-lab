package com.logistpulse.lab.domain;

import java.util.EnumSet;
import java.util.Set;

/** Máquina de estados del pedido (el "Formal Decision Gate" del laboratorio). */
public enum OrderStatus {
    CREATED, DISPATCHED, IN_TRANSIT, DELIVERED, CANCELLED;

    public Set<OrderStatus> next() {
        return switch (this) {
            case CREATED -> EnumSet.of(DISPATCHED, CANCELLED);
            case DISPATCHED -> EnumSet.of(IN_TRANSIT, CANCELLED);
            case IN_TRANSIT -> EnumSet.of(DELIVERED);
            case DELIVERED, CANCELLED -> EnumSet.noneOf(OrderStatus.class);
        };
    }

    public boolean canGoTo(OrderStatus target) {
        return next().contains(target);
    }
}
