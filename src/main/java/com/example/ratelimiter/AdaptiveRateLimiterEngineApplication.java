package com.example.ratelimiter;

import com.example.ratelimiter.config.RateLimiterProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Application entry point. Scheduling drives idle-state cleanup; configuration
 * properties provide validated limiter settings at startup.
 */
@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(RateLimiterProperties.class)
public class AdaptiveRateLimiterEngineApplication {
    public static void main(String[] args) {
        SpringApplication.run(AdaptiveRateLimiterEngineApplication.class, args);
    }
}
