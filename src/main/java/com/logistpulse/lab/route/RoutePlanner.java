package com.logistpulse.lab.route;

import com.logistpulse.lab.api.ApiException;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/**
 * Planificador de rutas. El resultado se cachea (Redis en el perfil docker, memoria en local);
 * el Timer solo mide los cálculos reales, es decir, los cache-miss.
 */
@Service
public class RoutePlanner {

    private final WarehouseGrid grid = WarehouseGrid.defaultLayout();
    private final AStarPathfinder finder = new AStarPathfinder(grid);
    private final Timer planTimer;

    public RoutePlanner(MeterRegistry registry) {
        this.planTimer = Timer.builder("logistpulse.route.plan")
                .description("Tiempo de cálculo A* (solo cache-miss)")
                .register(registry);
    }

    public WarehouseGrid grid() {
        return grid;
    }

    public boolean isWalkable(int x, int y) {
        return grid.walkable(x, y);
    }

    @Cacheable(cacheNames = "routes",
               key = "#from.x() + ':' + #from.y() + '>' + #to.x() + ':' + #to.y()")
    public RouteDto plan(Point from, Point to) {
        return planTimer.record(() -> finder.find(from, to))
                .orElseThrow(() -> new ApiException(422, "No existe ruta entre "
                        + from + " y " + to + " (celda bloqueada o fuera de la grilla)"));
    }
}
