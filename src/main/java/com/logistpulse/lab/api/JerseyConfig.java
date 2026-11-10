package com.logistpulse.lab.api;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.jersey.server.DefaultJerseyTagsProvider;
import io.micrometer.core.instrument.binder.jersey.server.MetricsApplicationEventListener;
import org.glassfish.jersey.server.ResourceConfig;
import org.springframework.stereotype.Component;

@Component
public class JerseyConfig extends ResourceConfig {

    public JerseyConfig(MeterRegistry registry) {
        // Spring Boot 4 ya no registra por sí solo las métricas de Jersey: sin esto no existe http_server_requests_*
        register(new MetricsApplicationEventListener(registry, new DefaultJerseyTagsProvider(), "http.server.requests", true));
        register(OrderResource.class);
        register(RobotResource.class);
        register(RouteResource.class);
        register(UiResource.class);
        register(ApiExceptionMapper.class);
        register(OptimisticLockMapper.class);
        register(ServiceUnavailableMapper.class);
        register(ConstraintViolationMapper.class);
    }
}
