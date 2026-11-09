package com.logistpulse.lab.route;

import java.io.Serializable;
import java.util.List;

/**
 * Ruta calculada: celdas (sin incluir el origen), costo y número de pasos.
 * {@code cost} es entero (redondeado hacia arriba) porque batería y ETA se calculan en unidades enteras;
 * {@code exactCost} conserva el valor real (con diagonales incluye √2).
 */
public record RouteDto(List<Point> path, int cost, int steps, double exactCost) implements Serializable {

    /** Ruta de costo entero (4 vecinos). */
    public RouteDto(List<Point> path, int cost, int steps) {
        this(path, cost, steps, cost);
    }
}
