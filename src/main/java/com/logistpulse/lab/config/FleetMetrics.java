package com.logistpulse.lab.config;

import com.logistpulse.lab.domain.RobotStatus;
import com.logistpulse.lab.repo.RobotRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.stereotype.Component;

/** Gauges de la flota, visibles en /actuator/prometheus y en el dashboard de Grafana. */
@Component
public class FleetMetrics implements MeterBinder {

    private final RobotRepository robots;

    public FleetMetrics(RobotRepository robots) {
        this.robots = robots;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        Gauge.builder("logistpulse.robots.idle", robots, r -> r.countByStatus(RobotStatus.IDLE))
                .description("AMR libres").register(registry);
        Gauge.builder("logistpulse.robots.busy", robots, r -> r.countByStatus(RobotStatus.BUSY))
                .description("AMR en misión").register(registry);
    }
}
