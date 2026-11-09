package com.logistpulse.lab.route;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.ArrayList;
import java.util.List;
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
        // Directo por la zona congestionada (costo 2 por celda) costaría 2+2+2+1 = 7; rodeando por la fila 2 cuesta 6.
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

    @Test
    void crossesCongestedZoneWhenItIsCheaperThanDetour() {
        // (5,2)->(5,5): recto por (5,3),(5,4) cuesta 2+2+1 = 5 en 3 pasos; rodear por el pasillo cuesta 7 en 7 pasos.
        // Con costo 5 se rodeaba (Parte 5.1); con costo 2 atravesar es lo óptimo.
        RouteDto r = finder.find(new Point(5, 2), new Point(5, 5)).orElseThrow();
        assertThat(r.cost()).isEqualTo(5);
        assertThat(r.steps()).isEqualTo(3);
        assertThat(r.path()).containsExactly(new Point(5, 3), new Point(5, 4), new Point(5, 5));
    }

    @Test
    void destinationInsideCongestedZoneCostsTheCongestedCells() {
        // (5,2)->(5,4): dos celdas congestionadas, 2+2
        assertThat(finder.find(new Point(5, 2), new Point(5, 4)).orElseThrow().cost()).isEqualTo(4);
    }

    @Test
    void manhattanHeuristicNeverOverestimatesTheRealCost() {
        // admisibilidad: h(n) <= costo óptimo real para todo par de celdas transitables
        for (Point a : walkableCells()) {
            for (Point b : walkableCells()) {
                assertThat(finder.heuristic(a, b)).as("%s -> %s", a, b)
                        .isLessThanOrEqualTo(finder.find(a, b).orElseThrow().exactCost() + 1e-9);
            }
        }
    }

    @Test
    void costMatchesReferenceDijkstra() {
        for (Point a : walkableCells()) {
            for (Point b : walkableCells()) {
                assertThat(finder.find(a, b).orElseThrow().exactCost()).as("%s -> %s", a, b)
                        .isCloseTo(DijkstraReference.cost(grid, a, b, false), within(1e-9));
            }
        }
    }

    @Test
    void orthogonalCostIsAnExactInteger() {
        RouteDto r = finder.find(new Point(0, 0), new Point(11, 7)).orElseThrow();
        assertThat(r.exactCost()).isEqualTo(r.cost());
    }

    private List<Point> walkableCells() {
        List<Point> cells = new ArrayList<>();
        for (int y = 0; y < grid.height(); y++) {
            for (int x = 0; x < grid.width(); x++) {
                if (grid.walkable(x, y)) {
                    cells.add(new Point(x, y));
                }
            }
        }
        return cells;
    }
}
