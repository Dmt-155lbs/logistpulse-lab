package com.logistpulse.lab.route;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class AStarPathfinderTest {

    private final WarehouseGrid grid = WarehouseGrid.defaultLayout();
    private final AStarPathfinder finder = new AStarPathfinder(grid);

    @Test
    void straightCorridorCostsOnePerStep() {
        RouteDto r = finder.find(new Point(0, 0), new Point(11, 0)).orElseThrow();
        assertThat(r.steps()).isEqualTo(11);
        assertThat(r.cost()).isEqualTo(11);
    }

    @Test
    void blockedDestinationHasNoRoute() {
        assertThat(finder.find(new Point(0, 0), new Point(1, 1))).isEmpty();
    }

    @Test
    void outOfBoundsHasNoRoute() {
        assertThat(finder.find(new Point(0, 0), new Point(99, 99))).isEmpty();
    }

    @Test
    void pathNeverCrossesShelves() {
        RouteDto r = finder.find(new Point(0, 0), new Point(11, 7)).orElseThrow();
        assertThat(r.path()).allMatch(grid::walkable);
        assertThat(r.path().get(r.path().size() - 1)).isEqualTo(new Point(11, 7));
    }

    @Test
    void avoidsCongestedZoneWhenDetourIsCheaper() {
        // Directo por la zona congestionada costaría 16; rodeando por la fila 2 cuesta 6.
        Optional<RouteDto> r = finder.find(new Point(3, 3), new Point(7, 3));
        assertThat(r).isPresent();
        assertThat(r.get().cost()).isEqualTo(6);
        assertThat(r.get().path()).noneMatch(p -> grid.rows().get(p.y()).charAt(p.x()) == ',');
    }

    @Test
    void consecutiveCellsAreAdjacent() {
        RouteDto r = finder.find(new Point(0, 0), new Point(10, 5)).orElseThrow();
        Point prev = new Point(0, 0);
        for (Point p : r.path()) {
            assertThat(Math.abs(p.x() - prev.x()) + Math.abs(p.y() - prev.y())).isEqualTo(1);
            prev = p;
        }
    }
}
