package com.logistpulse.lab.api;

import com.logistpulse.lab.api.dto.RobotDto;
import com.logistpulse.lab.service.RobotService;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
@Path("/api/robots")
@Produces(MediaType.APPLICATION_JSON)
public class RobotResource {

    private final RobotService service;

    public RobotResource(RobotService service) {
        this.service = service;
    }

    @GET
    public List<RobotDto> list() {
        return service.list();
    }

    @POST
    @Path("/{id}/charge")
    public RobotDto charge(@PathParam("id") Long id) {
        return service.charge(id);
    }
}
