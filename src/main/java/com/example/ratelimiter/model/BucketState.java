package com.example.ratelimiter.model;

/**
 * Mutable state for one key. Synchronization is deliberately scoped to a
 * single bucket, so unrelated clients never contend with each other.
 */
public final class BucketState {
    private double tokens;
    private double effectiveRate;
    private long lastRefillNanos;
    private volatile long lastAccessNanos;

    private long allowedRequests;
    private long rejectedRequests;
    private long totalFeedback;
    private long totalErrors;
    private double totalLatencyMs;

    // Window counters reset after each adaptation; totals remain for diagnostics.
    private int windowSamples;
    private int windowErrors;
    private double windowLatencyMs;

    public BucketState(RateLimitPolicy policy, long nowNanos) {
        this.tokens = policy.capacity();
        this.effectiveRate = policy.initialRatePerSecond();
        this.lastRefillNanos = nowNanos;
        this.lastAccessNanos = nowNanos;
    }

    public synchronized RateLimitDecision tryAcquire(
            RateLimitPolicy policy, int cost, long nowNanos) {
        refill(policy, nowNanos);
        lastAccessNanos = nowNanos;

        if (tokens >= cost) {
            tokens -= cost;
            allowedRequests++;
            return new RateLimitDecision(true, (long) tokens, effectiveRate, 0);
        }

        rejectedRequests++;
        long retryAfterMs = (long) Math.ceil((cost - tokens) * 1_000.0 / effectiveRate);
        return new RateLimitDecision(false, (long) tokens, effectiveRate, retryAfterMs);
    }

    public synchronized void recordFeedback(
            RateLimitPolicy policy, long latencyMs, boolean successful, long nowNanos) {
        refill(policy, nowNanos);
        lastAccessNanos = nowNanos;
        totalFeedback++;
        totalLatencyMs += latencyMs;
        windowSamples++;
        windowLatencyMs += latencyMs;

        if (!successful) {
            totalErrors++;
            windowErrors++;
        }

        if (windowSamples >= policy.evaluationWindow()) {
            adapt(policy);
            windowSamples = 0;
            windowErrors = 0;
            windowLatencyMs = 0;
        }
    }

    public synchronized RateLimitStats snapshot(
            BucketKey bucketKey, RateLimitPolicy policy, long nowNanos) {
        refill(policy, nowNanos);
        double errorRate = totalFeedback == 0 ? 0 : (double) totalErrors / totalFeedback;
        double averageLatency = totalFeedback == 0 ? 0 : totalLatencyMs / totalFeedback;
        return new RateLimitStats(
                bucketKey.policyId(),
                bucketKey.key(),
                allowedRequests,
                rejectedRequests,
                totalFeedback,
                effectiveRate,
                tokens,
                errorRate,
                averageLatency);
    }

    public long lastAccessNanos() {
        return lastAccessNanos;
    }

    private void refill(RateLimitPolicy policy, long nowNanos) {
        // A policy may be updated while this bucket exists, so reapply its bounds.
        effectiveRate = Math.max(
                policy.minimumRatePerSecond(),
                Math.min(policy.maximumRatePerSecond(), effectiveRate));
        long elapsedNanos = Math.max(0, nowNanos - lastRefillNanos);
        if (elapsedNanos > 0) {
            tokens = Math.min(
                    policy.capacity(),
                    tokens + elapsedNanos * effectiveRate / 1_000_000_000.0);
            lastRefillNanos = nowNanos;
        } else {
            tokens = Math.min(tokens, policy.capacity());
        }
    }

    private void adapt(RateLimitPolicy policy) {
        double errorRate = (double) windowErrors / windowSamples;
        double averageLatency = windowLatencyMs / windowSamples;
        boolean overloaded = errorRate > policy.maximumErrorRate()
                || averageLatency > policy.targetLatencyMs();

        if (overloaded) {
            // Multiplicative decrease reacts quickly and compounds under sustained load.
            effectiveRate = Math.max(
                    policy.minimumRatePerSecond(),
                    effectiveRate * (1 - policy.adaptationFactor()));
            return;
        }

        // Additive recovery avoids a sudden traffic surge after an unhealthy period.
        double increase = policy.maximumRatePerSecond()
                * policy.adaptationFactor() / policy.evaluationWindow();
        effectiveRate = Math.min(policy.maximumRatePerSecond(), effectiveRate + increase);
    }
}
