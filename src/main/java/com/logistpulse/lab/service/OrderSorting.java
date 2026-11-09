package com.logistpulse.lab.service;

import com.logistpulse.lab.api.ApiException;
import java.util.Locale;
import java.util.Map;
import org.springframework.data.domain.Sort;

/**
 * Convierte el parámetro ?sort=campo,dir en un Sort de Spring Data usando una lista blanca.
 * Nunca se pasa texto del cliente directamente a Sort.by(): evita ordenar por columnas internas
 * (version, warehouse...) o provocar errores 500 de JPA con propiedades inexistentes.
 */
public final class OrderSorting {

    /** Nombre público del campo -> propiedad de la entidad Order. */
    static final Map<String, String> ALLOWED = Map.of(
            "id", "id",
            "code", "code",
            "customer", "customerName",
            "status", "status",
            "createdAt", "createdAt",
            "etaMinutes", "etaMinutes");

    static final Sort DEFAULT = Sort.by(Sort.Direction.DESC, "id");

    private OrderSorting() {
    }

    public static Sort parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return DEFAULT;
        }
        String[] parts = raw.split(",", -1);
        if (parts.length > 2) {
            throw new ApiException(400, "Parámetro sort inválido: use campo[,asc|desc]");
        }
        String property = ALLOWED.get(parts[0].trim());
        if (property == null) {
            throw new ApiException(400, "Campo de orden no permitido: '" + parts[0].trim()
                    + "'. Permitidos: " + ALLOWED.keySet().stream().sorted().toList());
        }
        Sort.Direction dir = Sort.Direction.ASC;
        if (parts.length == 2) {
            String d = parts[1].trim().toLowerCase(Locale.ROOT);
            dir = switch (d) {
                case "asc" -> Sort.Direction.ASC;
                case "desc" -> Sort.Direction.DESC;
                default -> throw new ApiException(400, "Dirección de orden inválida: '" + parts[1].trim() + "' (asc o desc)");
            };
        }
        Sort sort = Sort.by(dir, property);
        // desempate estable por id: sin él la paginación puede repetir u omitir filas con valores iguales
        return "id".equals(property) ? sort : sort.and(Sort.by(dir, "id"));
    }
}
