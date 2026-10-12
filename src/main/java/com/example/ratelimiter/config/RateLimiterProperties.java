package com.example.ratelimiter.config;

import com.example.ratelimiter.model.RateLimitPolicy;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.Min;
import javax.validation.constraints.Positive;
import java.time.Duration;

/**
 * Type-safe external configuration for memory management and the startup
 * policy. Defaults allow local execution while remaining overrideable through
 * properties, environment variables, or command-line arguments.
 */
@Validated
@ConfigurationProperties(prefix = "rate-limiter")
public class RateLimiterProperties {
    @Min(1)
    private int maximumBuckets = 100_000;
    private Duration idleTtl = Duration.ofMinutes(30);
    private final DefaultPolicy defaultPolicy = new DefaultPolicy();

    public int getMaximumBuckets() {
        return maximumBuckets;
    }

    public void setMaximumBuckets(int maximumBuckets) {
        this.maximumBuckets = maximumBuckets;
    }

    public Duration getIdleTtl() {
        return idleTtl;
    }

    public void setIdleTtl(Duration idleTtl) {
        this.idleTtl = idleTtl;
    }

    public DefaultPolicy getDefaultPolicy() {
        return defaultPolicy;
    }

    public static class DefaultPolicy {
        @Min(1)
        private int capacity = 100;
        @Positive
        private double initialRatePerSecond = 100;
        @Positive
        private double minimumRatePerSecond = 10;
        @Positive
        private double maximumRatePerSecond = 1_000;
        @Positive
        private long targetLatencyMs = 200;
        private double maximumErrorRate = 0.05;
        @Min(1)
        private int evaluationWindow = 50;
        private double adaptationFactor = 0.20;

        /**
         * Converts mutable configuration binding state into a validated,
         * immutable object used by the request hot path.
         */
        public RateLimitPolicy toPolicy() {
            return new RateLimitPolicy(
                    "default", capacity, initialRatePerSecond, minimumRatePerSecond,
                    maximumRatePerSecond, targetLatencyMs, maximumErrorRate,
                    evaluationWindow, adaptationFactor);
        }

        public int getCapacity() { return capacity; }
        public void setCapacity(int capacity) { this.capacity = capacity; }
        public double getInitialRatePerSecond() { return initialRatePerSecond; }
        public void setInitialRatePerSecond(double value) { this.initialRatePerSecond = value; }
        public double getMinimumRatePerSecond() { return minimumRatePerSecond; }
        public void setMinimumRatePerSecond(double value) { this.minimumRatePerSecond = value; }
        public double getMaximumRatePerSecond() { return maximumRatePerSecond; }
        public void setMaximumRatePerSecond(double value) { this.maximumRatePerSecond = value; }
        public long getTargetLatencyMs() { return targetLatencyMs; }
        public void setTargetLatencyMs(long targetLatencyMs) { this.targetLatencyMs = targetLatencyMs; }
        public double getMaximumErrorRate() { return maximumErrorRate; }
        public void setMaximumErrorRate(double value) { this.maximumErrorRate = value; }
        public int getEvaluationWindow() { return evaluationWindow; }
        public void setEvaluationWindow(int evaluationWindow) { this.evaluationWindow = evaluationWindow; }
        public double getAdaptationFactor() { return adaptationFactor; }
        public void setAdaptationFactor(double value) { this.adaptationFactor = value; }
    }
}
