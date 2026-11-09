package com.logistpulse.lab.route;

import com.logistpulse.lab.api.ApiException;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/**
 * Planificador de rutas. El resultado se cachea (Redis en el perfil docker, memoria en local);
 * el Timer solo mide los cálculos reales, es decir, los cache-miss.
 * <p>
 * Invalidación: la clave de caché incluye la <b>versión del plano</b> (hash de la grilla, de los costos y del modo
 * diagonal), de modo que un plano distinto nunca lee rutas de otro; además {@link #replaceLayout} vacía la caché
 * con {@code @CacheEvict} para no dejar entradas huérfanas, y el TTL del perfil docker acota lo que quede.
 */
@Service
public class RoutePlanner {

    /** Plano + buscador + versión, intercambiados atómicamente al cambiar el plano. */
    private record Plan(WarehouseGrid grid, AStarPathfinder finder, String version) {
    }

    /** Súbelo si cambia la forma serializada de {@link RouteDto}: las entradas antiguas dejan de leerse. */
    static final int CACHE_SCHEMA = 2;

    private final boolean diagonal;
    private final Timer planTimer;
    private volatile Plan plan;

    public RoutePlanner(MeterRegistry registry, @Value("${lab.route.diagonal:false}") boolean diagonal) {
        this.diagonal = diagonal;
        this.plan = build(WarehouseGrid.defaultLayout(), diagonal);
        this.planTimer = Timer.builder("logistpulse.route.plan")
                .description("Tiempo de cálculo A* (solo cache-miss)")
                .register(registry);
    }

    public WarehouseGrid grid() {
        return plan.grid();
    }

    /** Identificador del plano vigente; forma parte de la clave de caché. */
    public String planVersion() {
        return plan.version();
    }

    public boolean isWalkable(int x, int y) {
        return plan.grid().walkable(x, y);
    }

    @Cacheable(cacheNames = "routes",
               key = "#root.target.planVersion() + ':' + #from.x() + ':' + #from.y() + '>' + #to.x() + ':' + #to.y()")
    public RouteDto plan(Point from, Point to) {
        AStarPathfinder finder = plan.finder();
        return planTimer.record(() -> finder.find(from, to))
                .orElseThrow(() -> new ApiException(422, "No existe ruta entre "
                        + from + " y " + to + " (celda bloqueada o fuera de la grilla)"));
    }

    /** Cambia el plano del almacén e invalida todas las rutas cacheadas. */
    @CacheEvict(cacheNames = "routes", allEntries = true)
    public void replaceLayout(List<String> rows) {
        this.plan = build(new WarehouseGrid(rows), diagonal);
    }

    private static Plan build(WarehouseGrid grid, boolean diagonal) {
        return new Plan(grid, new AStarPathfinder(grid, diagonal), version(grid, diagonal));
    }

    static String version(WarehouseGrid grid, boolean diagonal) {
        String material = "dto" + CACHE_SCHEMA + "|" + String.join("/", grid.rows()) + "|" + WarehouseGrid.FREE_COST + ","
                + WarehouseGrid.CONGESTED_COST + "|" + (diagonal ? "8" : "4");
        try {
            byte[] h = MessageDigest.getInstance("SHA-256").digest(material.getBytes(StandardCharsets.UTF_8));
            return "v" + HexFormat.of().formatHex(h, 0, 4);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
