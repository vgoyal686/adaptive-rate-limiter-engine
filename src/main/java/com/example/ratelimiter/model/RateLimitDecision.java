package com.example.ratelimiter.model;

/**
 * Immutable result returned by one token acquisition attempt.
 *
 * @param retryAfterMs zero when allowed; otherwise the estimated wait for enough tokens
 */
public record RateLimitDecision(
        boolean allowed,
        long remainingTokens,
        double effectiveRatePerSecond,
        long retryAfterMs) {
}
