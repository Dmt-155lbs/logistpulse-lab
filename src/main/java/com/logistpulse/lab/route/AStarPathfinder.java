package com.logistpulse.lab.route;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.PriorityQueue;

/** A* sobre grilla de 4 vecinos con costos por celda. Heurística: distancia Manhattan (admisible, costo mínimo = 1). */
public class AStarPathfinder {

    private static final int[][] DIRS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    private final WarehouseGrid grid;

    public AStarPathfinder(WarehouseGrid grid) {
        this.grid = grid;
    }

    public Optional<RouteDto> find(Point start, Point goal) {
        if (!grid.walkable(start) || !grid.walkable(goal)) {
            return Optional.empty();
        }
        int w = grid.width();
        int size = w * grid.height();
        int[] g = new int[size];
        int[] prev = new int[size];
        boolean[] closed = new boolean[size];
        Arrays.fill(g, Integer.MAX_VALUE);
        Arrays.fill(prev, -1);

        int s = start.y() * w + start.x();
        int t = goal.y() * w + goal.x();
        g[s] = 0;

        // elementos: {índice de celda, f = g + h}
        PriorityQueue<int[]> open = new PriorityQueue<>(Comparator.comparingInt(a -> a[1]));
        open.add(new int[]{s, manhattan(start.x(), start.y(), goal)});

        while (!open.isEmpty()) {
            int cur = open.poll()[0];
            if (closed[cur]) {
                continue;
            }
            closed[cur] = true;
            if (cur == t) {
                return Optional.of(rebuild(prev, t, g[t], w));
            }
            int cx = cur % w;
            int cy = cur / w;
            for (int[] d : DIRS) {
                int nx = cx + d[0];
                int ny = cy + d[1];
                if (!grid.walkable(nx, ny)) {
                    continue;
                }
                int ni = ny * w + nx;
                if (closed[ni]) {
                    continue;
                }
                int ng = g[cur] + grid.cost(nx, ny);
                if (ng < g[ni]) {
                    g[ni] = ng;
                    prev[ni] = cur;
                    open.add(new int[]{ni, ng + manhattan(nx, ny, goal)});
                }
            }
        }
        return Optional.empty();
    }

    private static int manhattan(int x, int y, Point goal) {
        return Math.abs(x - goal.x()) + Math.abs(y - goal.y());
    }

    private static RouteDto rebuild(int[] prev, int target, int cost, int w) {
        List<Point> path = new ArrayList<>();
        for (int i = target; prev[i] != -1; i = prev[i]) {
            path.add(new Point(i % w, i / w));
        }
        Collections.reverse(path);
        return new RouteDto(path, cost, path.size());
    }
}
