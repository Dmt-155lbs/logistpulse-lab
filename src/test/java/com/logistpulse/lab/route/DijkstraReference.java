package com.logistpulse.lab.route;

import java.util.Arrays;
import java.util.PriorityQueue;

/** Dijkstra sin heurística, usado como oráculo para verificar que A* es óptimo (misma regla de esquinas). */
final class DijkstraReference {

    private DijkstraReference() {
    }

    static double cost(WarehouseGrid grid, Point from, Point to, boolean diagonal) {
        int w = grid.width();
        double[] dist = new double[w * grid.height()];
        Arrays.fill(dist, Double.POSITIVE_INFINITY);
        dist[from.y() * w + from.x()] = 0;
        PriorityQueue<double[]> q = new PriorityQueue<>((a, b) -> Double.compare(a[1], b[1]));
        q.add(new double[]{from.y() * w + from.x(), 0});
        while (!q.isEmpty()) {
            double[] top = q.poll();
            int cur = (int) top[0];
            if (top[1] > dist[cur]) {
                continue;
            }
            int cx = cur % w;
            int cy = cur / w;
            for (int dy = -1; dy <= 1; dy++) {
                for (int dx = -1; dx <= 1; dx++) {
                    boolean diag = dx != 0 && dy != 0;
                    if ((dx == 0 && dy == 0) || (diag && !diagonal)) {
                        continue;
                    }
                    int nx = cx + dx;
                    int ny = cy + dy;
                    if (!grid.walkable(nx, ny)) {
                        continue;
                    }
                    if (diag && !(grid.walkable(cx + dx, cy) && grid.walkable(cx, cy + dy))) {
                        continue;
                    }
                    double nd = dist[cur] + grid.cost(nx, ny) * (diag ? Math.sqrt(2) : 1.0);
                    int ni = ny * w + nx;
                    if (nd < dist[ni]) {
                        dist[ni] = nd;
                        q.add(new double[]{ni, nd});
                    }
                }
            }
        }
        return dist[to.y() * w + to.x()];
    }
}
