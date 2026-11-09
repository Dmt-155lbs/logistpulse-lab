package com.logistpulse.lab.api;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Component;

/** Sirve la consola web mínima (Jersey no sirve estáticos como Spring MVC). */
@Component
@Path("/")
public class UiResource {

    @GET
    @Produces("text/html;charset=UTF-8")
    public String index() throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/ui/index.html")) {
            if (in == null) {
                throw new ApiException(404, "ui/index.html no encontrado");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
