package com.logistpulse.lab.config;

import com.logistpulse.lab.domain.RobotStatus;
import com.logistpulse.lab.repo.RobotRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Gauges de la flota, visibles en /actuator/prometheus y en el dashboard de Grafana. Las lecturas a la BD se memorizan
 * unos segundos (Parte 8.3: con la BD caída, cada gauge esperaba el timeout de la pool y el scrape no terminaba).
 */
@Component
public class FleetMetrics implements MeterBinder {

    private record Counts(long idle, long busy) {
    }

    private final TtlCache<Counts> cache;

    public FleetMetrics(RobotRepository robots, @Value("${lab.metrics.orders-cache-ms:3000}") long cacheMs) {
        this.cache = new TtlCache<>(() -> new Counts(robots.countByStatus(RobotStatus.IDLE),
                robots.countByStatus(RobotStatus.BUSY)), Duration.ofMillis(cacheMs));
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        Gauge.builder("logistpulse.robots.idle", cache, c -> c.get().idle())
                .description("AMR libres").register(registry);
        Gauge.builder("logistpulse.robots.busy", cache, c -> c.get().busy())
                .description("AMR en misión").register(registry);
    }
}
