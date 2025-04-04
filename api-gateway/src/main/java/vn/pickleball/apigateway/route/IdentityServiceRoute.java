package vn.pickleball.apigateway.route;


import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.builder.Buildable;
import org.springframework.cloud.gateway.route.builder.PredicateSpec;
import org.springframework.stereotype.Component;

import java.util.function.Function;

@Component
public class IdentityServiceRoute implements ServiceRoute {

    public static final String ROUTE_ID = "identity-service";

    @Value("${service-url.identity-service}")
    private String serviceUri;

    @Override
    public Function<PredicateSpec, Buildable<Route>> route() {
        return route -> route.path("/api/identity/**")
                .filters(f -> f.rewritePath("/api/(?<segment>.*)", "/${segment}"))
                .uri(serviceUri);
    }

    @Override
    public String getId() {
        return ROUTE_ID;
    }

}

