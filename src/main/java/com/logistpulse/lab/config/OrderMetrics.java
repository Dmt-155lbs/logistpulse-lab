package com.logistpulse.lab.config;

import com.logistpulse.lab.domain.OrderStatus;
import com.logistpulse.lab.repo.OrderRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Gauges de pedidos (Parte 8):
 *  - logistpulse_orders_by_status{status=...}: cuántos pedidos hay en cada estado (reto 8.6);
 *  - logistpulse_orders_dispatched_oldest_age_seconds: antigüedad del pedido DISPATCHED más viejo (SLO de 8.5).
 *
 * Costo: Prometheus lee 7 gauges por scrape (cada 5 s). Para no lanzar 7 consultas cada vez, una sola lectura
 * agregada (group by status + min(dispatchedAt)) se reutiliza durante lab.metrics.orders-cache-ms. Si la BD falla, el
 * gauge devuelve NaN (Micrometer captura la excepción) y el scrape de /actuator/prometheus sigue funcionando.
 */
@Component
public class OrderMetrics implements MeterBinder {

    /** Lectura agregada de la BD. */
    record Snapshot(Map<OrderStatus, Long> byStatus, double oldestDispatchedAgeSeconds) {
    }

    private final OrderRepository orders;
    private final TtlCache<Snapshot> cache;

    public OrderMetrics(OrderRepository orders, @Value("${lab.metrics.orders-cache-ms:3000}") long cacheMs) {
        this.orders = orders;
        this.cache = new TtlCache<>(this::read, Duration.ofMillis(cacheMs));
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        for (OrderStatus st : OrderStatus.values()) {
            Gauge.builder("logistpulse.orders.by.status", cache, c -> c.get().byStatus().getOrDefault(st, 0L))
                    .tag("status", st.name()).description("Pedidos por estado").register(registry);
        }
        Gauge.builder("logistpulse.orders.dispatched.oldest.age", cache, c -> c.get().oldestDispatchedAgeSeconds())
                .baseUnit("seconds").description("Segundos que lleva el pedido DISPATCHED más antiguo (0 si no hay)")
                .register(registry);
    }

    private Snapshot read() {
        Map<OrderStatus, Long> counts = new EnumMap<>(OrderStatus.class);
        for (Object[] row : orders.countGroupedByStatus()) {
            counts.put((OrderStatus) row[0], (Long) row[1]);
        }
        Instant oldest = orders.oldestDispatchedAt();
        double age = oldest == null ? 0 : Math.max(0, Duration.between(oldest, Instant.now()).toMillis() / 1000.0);
        return new Snapshot(counts, age);
    }
}
