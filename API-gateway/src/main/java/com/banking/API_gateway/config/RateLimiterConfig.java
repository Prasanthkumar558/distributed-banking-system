package com.banking.API_gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import reactor.core.publisher.Mono;

@Configuration
public class RateLimiterConfig {

    @Bean
    @Primary
    public KeyResolver keyResolver() {
        return exchange -> Mono.just(
                exchange.getRequest().getRemoteAddress() != null &&
                        exchange.getRequest().getRemoteAddress().getAddress() != null
                        ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
                        : "127.0.0.1"
        );
    }
}