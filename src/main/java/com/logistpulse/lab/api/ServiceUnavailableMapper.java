package com.logistpulse.lab.api;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.core.NestedRuntimeException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.transaction.CannotCreateTransactionException;

/**
 * Parte 8.3: una dependencia caída (BD, Redis) es un 503 con JSON y Retry-After, no un 500 con la página HTML de
 * Tomcat. Cualquier otro error de infraestructura de Spring sigue siendo un 500, pero también en JSON.
 */
@Provider
public class ServiceUnavailableMapper implements ExceptionMapper<NestedRuntimeException> {

    private static final Logger log = LoggerFactory.getLogger(ServiceUnavailableMapper.class);

    @Override
    public Response toResponse(NestedRuntimeException e) {
        if (isInfrastructureDown(e)) {
            log.warn("Dependencia no disponible: {}", NestedExceptionUtils.getMostSpecificCause(e).toString());
            return Response.status(503).header("Retry-After", "2").type(MediaType.APPLICATION_JSON)
                    .entity(Map.of("status", 503, "error", "Servicio temporalmente no disponible; reintenta en unos segundos"))
                    .build();
        }
        log.error("Error no controlado", e);
        return Response.status(500).type(MediaType.APPLICATION_JSON)
                .entity(Map.of("status", 500, "error", "Error interno")).build();
    }

    static boolean isInfrastructureDown(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause() == t ? null : t.getCause()) {
            if (t instanceof DataAccessResourceFailureException || t instanceof CannotCreateTransactionException
                    || t instanceof QueryTimeoutException || t instanceof TransientDataAccessException) {
                return true;
            }
        }
        return false;
    }
}
