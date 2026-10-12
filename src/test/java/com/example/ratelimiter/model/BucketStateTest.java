package com.example.ratelimiter.model;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class BucketStateTest {
    private static final RateLimitPolicy POLICY =
            new RateLimitPolicy("test", 10, 10, 1, 20, 100, 0.10, 2, 0.50);

    @Test
    void rejectsWhenBucketIsEmptyAndReturnsRetryDelay() {
        BucketState state = new BucketState(POLICY, 0);

        assertThat(state.tryAcquire(POLICY, 10, 0).allowed()).isTrue();
        RateLimitDecision rejected = state.tryAcquire(POLICY, 1, 0);

        assertThat(rejected.allowed()).isFalse();
        assertThat(rejected.retryAfterMs()).isEqualTo(100);
    }

    @Test
    void refillsTokensAccordingToElapsedMonotonicTime() {
        BucketState state = new BucketState(POLICY, 0);
        state.tryAcquire(POLICY, 10, 0);

        RateLimitDecision decision = state.tryAcquire(POLICY, 5, 500_000_000);

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.remainingTokens()).isZero();
    }

    @Test
    void decreasesRateForUnhealthyFeedbackAndRecoversForHealthyFeedback() {
        BucketState state = new BucketState(POLICY, 0);
        BucketKey key = new BucketKey("test", "client");

        state.recordFeedback(POLICY, 200, false, 0);
        state.recordFeedback(POLICY, 200, false, 0);
        assertThat(state.snapshot(key, POLICY, 0).effectiveRatePerSecond()).isEqualTo(5);

        state.recordFeedback(POLICY, 10, true, 0);
        state.recordFeedback(POLICY, 10, true, 0);
        assertThat(state.snapshot(key, POLICY, 0).effectiveRatePerSecond()).isEqualTo(10);
    }

    @Test
    void neverAllowsMoreThanCapacityUnderConcurrency() throws Exception {
        RateLimitPolicy policy = new RateLimitPolicy(
                "concurrent", 100, 1, 1, 1, 100, 0.1, 10, 0.1);
        BucketState state = new BucketState(policy, 0);
        AtomicInteger allowed = new AtomicInteger();
        List<Callable<Void>> calls = java.util.stream.IntStream.range(0, 1_000)
                .mapToObj(index -> (Callable<Void>) () -> {
                    if (state.tryAcquire(policy, 1, 0).allowed()) {
                        allowed.incrementAndGet();
                    }
                    return null;
                })
                .toList();

        var executor = Executors.newFixedThreadPool(16);
        try {
            executor.invokeAll(calls);
        } finally {
            executor.shutdownNow();
        }

        assertThat(allowed).hasValue(100);
    }
}
