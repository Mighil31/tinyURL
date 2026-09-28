package com.mighil.tinyurl.service;

import java.time.Clock;
import java.time.Duration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RateLimitConfig {

    @Bean
    RateLimiter creationRateLimiter() {
        return new SlidingWindowRateLimiter(Clock.systemUTC(), Duration.ofMinutes(1));
    }
}
