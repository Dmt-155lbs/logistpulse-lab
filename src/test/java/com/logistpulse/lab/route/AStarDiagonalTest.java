package com.logistpulse.lab.route;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class AStarDiagonalTest {

    private static final double SQRT2 = Math.sqrt(2);

    private final WarehouseGrid grid = WarehouseGrid.defaultLayout();
    private final AStarPathfinder diag = new AStarPathfinder(grid, true);
    private final AStarPathfinder ortho = new AStarPathfinder(grid);

    private static WarehouseGrid open4x4() {
        return new WarehouseGrid(List.of("....", "....", "....", "...."));
    }

    @Test
    void pureDiagonalCostsSqrt2PerStep() {
        RouteDto r = new AStarPathfinder(open4x4(), true).find(new Point(0, 0), new Point(3, 3)).orElseThrow();
        assertThat(r.steps()).isEqualTo(3);
        assertThat(r.exactCost()).isCloseTo(3 * SQRT2, within(1e-9));
        assertThat(r.cost()).isEqualTo(5);   // ceil(4.24): batería y ETA usan un entero conservador
    }

    @Test
    void sameGridWithoutDiagonalsNeedsSixSteps() {
        RouteDto r = new AStarPathfinder(open4x4()).find(new Point(0, 0), new Point(3, 3)).orElseThrow();
        assertThat(r.steps()).isEqualTo(6);
        assertThat(r.cost()).isEqualTo(6);
    }

    @Test
    void mixedMoveUsesOneDiagonalAndStraightSteps() {
        // (0,0)->(3,1): 2 rectos + 1 diagonal = 2 + sqrt(2)
        RouteDto r = new AStarPathfinder(open4x4(), true).find(new Point(0, 0), new Point(3, 1)).orElseThrow();
        assertThat(r.exactCost()).isCloseTo(2 + SQRT2, within(1e-9));
        assertThat(r.steps()).isEqualTo(3);
    }

    @Test
    void doesNotSqueezeBetweenTwoObstaclesTouchingAtTheCorner() {
        // (1,0) y (0,1) son estanterías: pasar de (0,0) a (1,1) "entre" ellas está prohibido, la celda queda encerrada.
        WarehouseGrid pinch = new WarehouseGrid(List.of(
                ".#.",
                "#..",
                "..."));
        assertThat(new AStarPathfinder(pinch, true).find(new Point(0, 0), new Point(1, 1))).isEmpty();
    }

    @Test
    void doesNotCutTheCornerOfASingleObstacleEither() {
        // Regla estricta: basta un obstáculo flanqueante para prohibir la diagonal (el AMR rozaría la esquina).
        WarehouseGrid g = new WarehouseGrid(List.of(
                "..",
                "#."));
        RouteDto r = new AStarPathfinder(g, true).find(new Point(0, 0), new Point(1, 1)).orElseThrow();
        assertThat(r.steps()).isEqualTo(2);
        assertThat(r.path()).containsExactly(new Point(1, 0), new Point(1, 1));
    }

    @Test
    void diagonalNeverCostsMoreThanOrthogonal() {
        for (Point a : walkableCells()) {
            for (Point b : walkableCells()) {
                double d = diag.find(a, b).orElseThrow().exactCost();
                double o = ortho.find(a, b).orElseThrow().exactCost();
                assertThat(d).as("%s -> %s", a, b).isLessThanOrEqualTo(o + 1e-9);
            }
        }
    }

    @Test
    void octileHeuristicIsAdmissible() {
        for (Point a : walkableCells()) {
            for (Point b : walkableCells()) {
                assertThat(diag.heuristic(a, b)).as("%s -> %s", a, b)
                        .isLessThanOrEqualTo(diag.find(a, b).orElseThrow().exactCost() + 1e-9);
            }
        }
    }

    @Test
    void costMatchesReferenceDijkstraWithDiagonals() {
        for (Point a : walkableCells()) {
            for (Point b : walkableCells()) {
                assertThat(diag.find(a, b).orElseThrow().exactCost()).as("%s -> %s", a, b)
                        .isCloseTo(DijkstraReference.cost(grid, a, b, true), within(1e-9));
            }
        }
    }

    @Test
    void diagonalPathsOnlyTouchWalkableCellsAndMoveOneCellAtATime() {
        RouteDto r = diag.find(new Point(0, 0), new Point(11, 7)).orElseThrow();
        Point prev = new Point(0, 0);
        for (Point p : r.path()) {
            assertThat(grid.walkable(p)).isTrue();
            assertThat(Math.max(Math.abs(p.x() - prev.x()), Math.abs(p.y() - prev.y()))).isEqualTo(1);
            prev = p;
        }
    }

    @Test
    void integerCostIsTheCeilingOfTheExactCost() {
        RouteDto r = diag.find(new Point(0, 0), new Point(11, 7)).orElseThrow();
        assertThat(r.cost()).isEqualTo((int) Math.ceil(r.exactCost()));
        assertThat(r.cost() - r.exactCost()).isGreaterThanOrEqualTo(0.0).isLessThan(1.0);
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
