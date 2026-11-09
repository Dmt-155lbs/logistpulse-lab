package com.logistpulse.lab.route;

import java.io.Serializable;

/** Celda de la grilla del almacén. Serializable porque viaja a Redis dentro de RouteDto. */
public record Point(int x, int y) implements Serializable {
}
