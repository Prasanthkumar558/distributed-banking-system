package com.banking.API_gateway.config;

import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;


@Configuration
public class RateLimiterConfig {

    public KeyResolver keyResolver() {

        return exchange -> Mono.just(
                exchange.getRequest()
                        .getRemoteAddress()
                        .getAddress()
                        .getHostAddress()
        );
    }

}
