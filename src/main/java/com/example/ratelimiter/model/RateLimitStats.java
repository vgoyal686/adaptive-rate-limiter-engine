package com.example.ratelimiter.model;

/**
 * Point-in-time, read-only view of a bucket's cumulative behavior and current
 * adaptive state.
 */
public record RateLimitStats(
        String policyId,
        String key,
        long allowedRequests,
        long rejectedRequests,
        long feedbackSamples,
        double effectiveRatePerSecond,
        double availableTokens,
        double observedErrorRate,
        double observedAverageLatencyMs) {
}
