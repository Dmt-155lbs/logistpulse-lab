package com.logistpulse.lab.api;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Traduce los fallos de Bean Validation (@Valid) a 400 con la lista de campos inválidos. */
@Provider
public class ConstraintViolationMapper implements ExceptionMapper<ConstraintViolationException> {

    @Override
    public Response toResponse(ConstraintViolationException e) {
        List<Map<String, String>> errors = new ArrayList<>();
        e.getConstraintViolations().stream()
                .sorted(Comparator.comparing((ConstraintViolation<?> v) -> fieldName(v.getPropertyPath()))
                        .thenComparing(ConstraintViolation::getMessage))
                .forEach(v -> errors.add(Map.of("field", fieldName(v.getPropertyPath()), "message", v.getMessage())));
        return Response.status(Response.Status.BAD_REQUEST)
                .type(MediaType.APPLICATION_JSON)
                .entity(Map.of("status", 400, "error", "Validación fallida", "violations", errors))
                .build();
    }

    /** La ruta llega como "create.input.customer": nos quedamos con el último nodo (el campo). */
    static String fieldName(Path path) {
        String last = "";
        for (Path.Node n : path) {
            last = n.getName();
        }
        return last;
    }
}
