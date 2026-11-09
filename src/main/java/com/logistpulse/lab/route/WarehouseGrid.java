package com.logistpulse.lab.route;

import java.util.List;

/**
 * Plano del almacén. Leyenda:
 *   '.' pasillo libre (costo 1)
 *   ',' zona congestionada (costo 5)
 *   '#' estantería / obstáculo (bloqueado)
 */
public final class WarehouseGrid {

    private final List<String> rows;
    private final int width;
    private final int height;

    public WarehouseGrid(List<String> rows) {
        this.rows = List.copyOf(rows);
        this.height = rows.size();
        this.width = rows.get(0).length();
    }

    public static WarehouseGrid defaultLayout() {
        return new WarehouseGrid(List.of(
                "............",
                ".####..####.",
                "............",
                ".#..,,,..#..",
                ".#..,,,..#..",
                "............",
                ".####..####.",
                "............"));
    }

    public int width() { return width; }
    public int height() { return height; }
    public List<String> rows() { return rows; }

    public boolean inBounds(int x, int y) {
        return x >= 0 && y >= 0 && x < width && y < height;
    }

    public boolean walkable(int x, int y) {
        return inBounds(x, y) && rows.get(y).charAt(x) != '#';
    }

    public boolean walkable(Point p) {
        return walkable(p.x(), p.y());
    }

    /** Costo de entrar a la celda. Solo válido si walkable(x, y). */
    public int cost(int x, int y) {
        return rows.get(y).charAt(x) == ',' ? 5 : 1;
    }
}
