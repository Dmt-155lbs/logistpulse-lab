package com.logistpulse.lab.route;

import java.io.Serializable;
import java.util.List;

/** Ruta calculada: celdas (sin incluir el origen), costo ponderado y número de pasos. */
public record RouteDto(List<Point> path, int cost, int steps) implements Serializable {
}
