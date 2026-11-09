package com.logistpulse.lab.api.dto;

import com.logistpulse.lab.domain.Order;
import com.logistpulse.lab.domain.OrderStatus;
import java.time.Instant;
import java.util.Map;

public record OrderDto(
        Long id,
        String code,
        String customer,
        int destX,
        int destY,
        OrderStatus status,
        String warehouse,
        String robot,
        Integer etaMinutes,
        Instant createdAt,
        Map<String, String> links) {

    /** Debe llamarse dentro de una transacción (warehouse y robot son LAZY). */
    public static OrderDto from(Order o) {
        return new OrderDto(o.getId(), o.getCode(), o.getCustomerName(), o.getDestX(), o.getDestY(),
                o.getStatus(), o.getWarehouse().getName(),
                o.getRobot() == null ? null : o.getRobot().getCode(),
                o.getEtaMinutes(), o.getCreatedAt(), Map.of());
    }

    public OrderDto withLinks(Map<String, String> newLinks) {
        return new OrderDto(id, code, customer, destX, destY, status, warehouse, robot,
                etaMinutes, createdAt, newLinks);
    }
}
