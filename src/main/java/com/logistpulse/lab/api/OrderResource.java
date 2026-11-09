package com.logistpulse.lab.api;

import com.logistpulse.lab.api.dto.OrderDto;
import com.logistpulse.lab.api.dto.OrderInput;
import com.logistpulse.lab.api.dto.PageDto;
import com.logistpulse.lab.api.dto.TransitionInput;
import com.logistpulse.lab.domain.OrderStatus;
import com.logistpulse.lab.service.OrderService;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
@Path("/api/orders")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class OrderResource {

    private final OrderService service;

    public OrderResource(OrderService service) {
        this.service = service;
    }

    @GET
    public PageDto<OrderDto> list(@QueryParam("status") OrderStatus status,
                                  @QueryParam("page") @DefaultValue("0") int page,
                                  @QueryParam("size") @DefaultValue("10") int size,
                                  @Context UriInfo uri) {
        PageDto<OrderDto> result = service.list(status, page, size);
        Map<String, String> links = new LinkedHashMap<>();
        links.put("self", pageUrl(uri, status, result.page(), result.size()));
        links.put("first", pageUrl(uri, status, 0, result.size()));
        if (result.page() > 0) {
            links.put("prev", pageUrl(uri, status, result.page() - 1, result.size()));
        }
        if (result.page() + 1 < result.totalPages()) {
            links.put("next", pageUrl(uri, status, result.page() + 1, result.size()));
        }
        links.put("last", pageUrl(uri, status, Math.max(result.totalPages() - 1, 0), result.size()));
        return new PageDto<>(result.content().stream().map(o -> linked(o, uri)).toList(),
                result.page(), result.size(), result.totalElements(), result.totalPages(), links);
    }

    @GET
    @Path("/{id}")
    public OrderDto get(@PathParam("id") Long id, @Context UriInfo uri) {
        return linked(service.get(id), uri);
    }

    @POST
    public Response create(@Valid OrderInput input, @Context UriInfo uri) {
        OrderDto created = service.create(input);
        URI location = uri.getAbsolutePathBuilder().path(String.valueOf(created.id())).build();
        return Response.created(location).entity(linked(created, uri)).build();   // 201 + Location
    }

    @POST
    @Path("/{id}/dispatch")
    public OrderDto dispatch(@PathParam("id") Long id, @Context UriInfo uri) {
        return linked(service.dispatch(id), uri);
    }

    @POST
    @Path("/{id}/transitions")
    public OrderDto transition(@PathParam("id") Long id, @Valid TransitionInput input, @Context UriInfo uri) {
        return linked(service.transition(id, input.to()), uri);
    }

    /** HATEOAS: los enlaces ofrecidos dependen del estado actual del pedido. */
    private OrderDto linked(OrderDto o, UriInfo uri) {
        String self = uri.getBaseUriBuilder().path("api/orders").path(String.valueOf(o.id())).build().toString();
        Map<String, String> links = new LinkedHashMap<>();
        links.put("self", self);
        links.put("collection", uri.getBaseUriBuilder().path("api/orders").build().toString());
        if (o.status() == OrderStatus.CREATED) {
            links.put("dispatch", self + "/dispatch");
        }
        for (OrderStatus next : o.status().next()) {
            if (next != OrderStatus.DISPATCHED) {
                links.put("to-" + next.name().toLowerCase(), self + "/transitions");
            }
        }
        return o.withLinks(links);
    }

    private String pageUrl(UriInfo uri, OrderStatus status, int page, int size) {
        var b = uri.getBaseUriBuilder().path("api/orders").queryParam("page", page).queryParam("size", size);
        if (status != null) {
            b.queryParam("status", status.name());
        }
        return b.build().toString();
    }
}
