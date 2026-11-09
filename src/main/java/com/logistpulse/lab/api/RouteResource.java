package com.logistpulse.lab.api;

import com.logistpulse.lab.api.dto.GridDto;
import com.logistpulse.lab.route.Point;
import com.logistpulse.lab.route.RouteDto;
import com.logistpulse.lab.route.RoutePlanner;
import com.logistpulse.lab.route.WarehouseGrid;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.springframework.stereotype.Component;

@Component
@Path("/api/routes")
@Produces(MediaType.APPLICATION_JSON)
public class RouteResource {

    private final RoutePlanner planner;

    public RouteResource(RoutePlanner planner) {
        this.planner = planner;
    }

    @GET
    public RouteDto route(@QueryParam("fromX") @DefaultValue("0") int fromX,
                          @QueryParam("fromY") @DefaultValue("0") int fromY,
                          @QueryParam("toX") int toX,
                          @QueryParam("toY") int toY) {
        return planner.plan(new Point(fromX, fromY), new Point(toX, toY));
    }

    @GET
    @Path("/grid")
    public GridDto grid() {
        WarehouseGrid g = planner.grid();
        return new GridDto(g.width(), g.height(), g.rows(), ". libre (1) · , congestionado (5) · # bloqueado");
    }
}
