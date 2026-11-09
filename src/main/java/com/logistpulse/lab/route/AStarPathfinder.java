package com.logistpulse.lab.route;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.PriorityQueue;

/**
 * A* sobre la grilla con costos por celda.
 * <ul>
 *   <li>Modo por defecto: 4 vecinos, heurística Manhattan × costo mínimo de celda.</li>
 *   <li>Modo {@code diagonal}: 8 vecinos. Un paso diagonal cuesta √2 × costo de la celda de destino y la
 *       heurística es la distancia octil × costo mínimo. No se permite cortar esquinas: un movimiento diagonal
 *       solo es válido si las dos celdas ortogonales que lo flanquean son transitables.</li>
 * </ul>
 * Los costos acumulados son {@code double} (√2 no es entero); en modo ortogonal todos los valores son enteros exactos.
 */
public class AStarPathfinder {

    static final double SQRT2 = Math.sqrt(2);
    private static final double EPS = 1e-9;

    private static final int[][] ORTHOGONAL = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    private static final int[][] WITH_DIAGONALS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};

    private record Node(int cell, double f) {
    }

    private final WarehouseGrid grid;
    private final boolean diagonal;

    public AStarPathfinder(WarehouseGrid grid) {
        this(grid, false);
    }

    public AStarPathfinder(WarehouseGrid grid, boolean diagonal) {
        this.grid = grid;
        this.diagonal = diagonal;
    }

    public boolean diagonal() {
        return diagonal;
    }

    public Optional<RouteDto> find(Point start, Point goal) {
        if (!grid.walkable(start) || !grid.walkable(goal)) {
            return Optional.empty();
        }
        int w = grid.width();
        int size = w * grid.height();
        double[] g = new double[size];
        int[] prev = new int[size];
        boolean[] closed = new boolean[size];
        Arrays.fill(g, Double.POSITIVE_INFINITY);
        Arrays.fill(prev, -1);

        int s = start.y() * w + start.x();
        int t = goal.y() * w + goal.x();
        g[s] = 0;

        PriorityQueue<Node> open = new PriorityQueue<>(Comparator.comparingDouble(Node::f));
        open.add(new Node(s, heuristic(start, goal)));

        int[][] dirs = diagonal ? WITH_DIAGONALS : ORTHOGONAL;
        while (!open.isEmpty()) {
            int cur = open.poll().cell();
            if (closed[cur]) {
                continue;
            }
            closed[cur] = true;
            if (cur == t) {
                return Optional.of(rebuild(prev, t, g[t], w));
            }
            int cx = cur % w;
            int cy = cur / w;
            for (int[] d : dirs) {
                int nx = cx + d[0];
                int ny = cy + d[1];
                if (!grid.walkable(nx, ny)) {
                    continue;
                }
                boolean diag = d[0] != 0 && d[1] != 0;
                // sin cortar esquinas: ambas celdas flanqueantes deben ser transitables
                if (diag && !(grid.walkable(cx + d[0], cy) && grid.walkable(cx, cy + d[1]))) {
                    continue;
                }
                int ni = ny * w + nx;
                if (closed[ni]) {
                    continue;
                }
                double ng = g[cur] + grid.cost(nx, ny) * (diag ? SQRT2 : 1.0);
                if (ng < g[ni] - EPS) {
                    g[ni] = ng;
                    prev[ni] = cur;
                    open.add(new Node(ni, ng + heuristic(nx, ny, goal)));
                }
            }
        }
        return Optional.empty();
    }

    /** Heurística entre dos celdas (visible para pruebas de admisibilidad). */
    double heuristic(Point from, Point goal) {
        return heuristic(from.x(), from.y(), goal);
    }

    private double heuristic(int x, int y, Point goal) {
        int dx = Math.abs(x - goal.x());
        int dy = Math.abs(y - goal.y());
        double min = WarehouseGrid.MIN_COST;
        if (!diagonal) {
            return min * (dx + dy);                                   // Manhattan
        }
        return min * (Math.max(dx, dy) + (SQRT2 - 1) * Math.min(dx, dy));   // octil
    }

    private static RouteDto rebuild(int[] prev, int target, double cost, int w) {
        List<Point> path = new ArrayList<>();
        for (int i = target; prev[i] != -1; i = prev[i]) {
            path.add(new Point(i % w, i / w));
        }
        Collections.reverse(path);
        return new RouteDto(path, (int) Math.ceil(cost - EPS), path.size(), cost);
    }
}
