package com.logistpulse.lab.route;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/** Caché con un contexto mínimo (sin Redis): @Cacheable, versión del plano en la clave y @CacheEvict. */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = RoutePlannerCacheTest.Config.class)
class RoutePlannerCacheTest {

    @Configuration
    @EnableCaching
    static class Config {
        @Bean
        MeterRegistry registry() {
            return new SimpleMeterRegistry();
        }

        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager("routes");
        }

        @Bean
        RoutePlanner routePlanner(MeterRegistry registry) {
            return new RoutePlanner(registry, false);
        }
    }

    @Autowired
    RoutePlanner planner;
    @Autowired
    MeterRegistry registry;
    @Autowired
    CacheManager cacheManager;

    private long misses() {
        return registry.get("logistpulse.route.plan").timer().count();
    }

    @Test
    void secondCallIsServedFromCacheAndLayoutChangeInvalidates() {
        long base = misses();
        String v1 = planner.planVersion();

        RouteDto a = planner.plan(new Point(0, 0), new Point(10, 5));
        RouteDto b = planner.plan(new Point(0, 0), new Point(10, 5));
        assertThat(misses() - base).as("el Timer solo cuenta el cache-miss").isEqualTo(1);
        assertThat(b).isEqualTo(a);
        assertThat(cacheManager.getCache("routes").get(v1 + ":0:0>10:5")).as("la clave lleva la versión").isNotNull();

        // plano nuevo: sin zona congestionada
        planner.replaceLayout(List.of(
                "............",
                ".####..####.",
                "............",
                ".#........#.",
                ".#........#.",
                "............",
                ".####..####.",
                "............"));
        assertThat(planner.planVersion()).isNotEqualTo(v1);
        assertThat(cacheManager.getCache("routes").get(v1 + ":0:0>10:5")).as("@CacheEvict(allEntries)").isNull();

        RouteDto c = planner.plan(new Point(5, 2), new Point(5, 5));
        assertThat(misses() - base).as("tras cambiar el plano se recalcula").isEqualTo(2);
        assertThat(c.cost()).isEqualTo(3);   // sin congestión el camino recto cuesta 3
    }

    @Test
    void versionDependsOnLayoutAndDiagonalMode() {
        WarehouseGrid g = WarehouseGrid.defaultLayout();
        assertThat(RoutePlanner.version(g, false)).isEqualTo(RoutePlanner.version(g, false));
        assertThat(RoutePlanner.version(g, false)).isNotEqualTo(RoutePlanner.version(g, true));
        assertThat(RoutePlanner.version(g, false))
                .isNotEqualTo(RoutePlanner.version(new WarehouseGrid(List.of("....", "....")), false));
    }
}
