package com.banking.API_gateway.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GatewayRoutesConfig {

    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()
                .route("account-service", r -> r
                        .path("/api/v1/accounts/**")
                        .uri("http://localhost:8081"))
                .route("transaction-service", r -> r
                        .path("/api/v1/transactions/**")
                        .uri("http://localhost:8082"))
                .build();
    }
}