package com.logistpulse.lab.api;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.util.Map;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

/** Dos operaciones concurrentes sobre el mismo pedido/robot: la perdedora recibe 409. */
@Provider
public class OptimisticLockMapper implements ExceptionMapper<ObjectOptimisticLockingFailureException> {

    @Override
    public Response toResponse(ObjectOptimisticLockingFailureException e) {
        return Response.status(409)
                .type(MediaType.APPLICATION_JSON)
                .entity(Map.of("status", 409, "error", "Conflicto de concurrencia, reintenta la operación"))
                .build();
    }
}
