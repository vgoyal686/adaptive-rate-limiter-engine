package com.example.ratelimiter.model;

/**
 * Immutable policy used by the hot path. Values are validated once, when the
 * policy is created, rather than on every rate-limit check.
 */
public record RateLimitPolicy(
        String id,
        int capacity,
        double initialRatePerSecond,
        double minimumRatePerSecond,
        double maximumRatePerSecond,
        long targetLatencyMs,
        double maximumErrorRate,
        int evaluationWindow,
        double adaptationFactor) {

    public RateLimitPolicy {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Policy id must not be blank");
        }
        if (capacity <= 0) {
            throw new IllegalArgumentException("Capacity must be positive");
        }
        if (minimumRatePerSecond <= 0
                || initialRatePerSecond < minimumRatePerSecond
                || maximumRatePerSecond < initialRatePerSecond) {
            throw new IllegalArgumentException("Rates must satisfy 0 < minimum <= initial <= maximum");
        }
        if (targetLatencyMs <= 0) {
            throw new IllegalArgumentException("Target latency must be positive");
        }
        if (maximumErrorRate < 0 || maximumErrorRate > 1) {
            throw new IllegalArgumentException("Maximum error rate must be between 0 and 1");
        }
        if (evaluationWindow <= 0) {
            throw new IllegalArgumentException("Evaluation window must be positive");
        }
        if (adaptationFactor <= 0 || adaptationFactor >= 1) {
            throw new IllegalArgumentException("Adaptation factor must be between 0 and 1");
        }
    }
}
