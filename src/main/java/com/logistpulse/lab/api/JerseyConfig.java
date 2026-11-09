package com.logistpulse.lab.api;

import org.glassfish.jersey.server.ResourceConfig;
import org.springframework.stereotype.Component;

@Component
public class JerseyConfig extends ResourceConfig {

    public JerseyConfig() {
        register(OrderResource.class);
        register(RobotResource.class);
        register(RouteResource.class);
        register(UiResource.class);
        register(ApiExceptionMapper.class);
        register(OptimisticLockMapper.class);
    }
}
