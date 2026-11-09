package com.logistpulse.lab.api;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.util.Map;

@Provider
public class ApiExceptionMapper implements ExceptionMapper<ApiException> {

    @Override
    public Response toResponse(ApiException e) {
        return Response.status(e.getStatus())
                .type(MediaType.APPLICATION_JSON)
                .entity(Map.of("status", e.getStatus(), "error", e.getMessage()))
                .build();
    }
}
