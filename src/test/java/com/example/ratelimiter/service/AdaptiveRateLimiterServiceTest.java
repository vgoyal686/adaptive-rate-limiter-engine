package com.example.ratelimiter.service;

import com.example.ratelimiter.config.RateLimiterProperties;
import com.example.ratelimiter.exception.ResourceNotFoundException;
import com.example.ratelimiter.model.RateLimitPolicy;
import com.example.ratelimiter.repository.InMemoryBucketRepository;
import com.example.ratelimiter.repository.InMemoryPolicyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdaptiveRateLimiterServiceTest {
    private AdaptiveRateLimiterService service;

    @BeforeEach
    void setUp() {
        service = new AdaptiveRateLimiterService(
                new InMemoryPolicyRepository(),
                new InMemoryBucketRepository(100),
                new RateLimiterProperties());
        service.initializeDefaultPolicy();
    }

    @Test
    void createsIndependentBucketsForDifferentKeys() {
        assertThat(service.acquire("default", "client-a", 100).allowed()).isTrue();
        assertThat(service.acquire("default", "client-a", 1).allowed()).isFalse();
        assertThat(service.acquire("default", "client-b", 100).allowed()).isTrue();
        assertThat(service.bucketCount()).isEqualTo(2);
    }

    @Test
    void recordsFeedbackAndExposesStats() {
        service.acquire("default", "client", 1);
        service.recordFeedback("default", "client", 25, true);

        var stats = service.getStats("default", "client");

        assertThat(stats.allowedRequests()).isOne();
        assertThat(stats.feedbackSamples()).isOne();
        assertThat(stats.observedAverageLatencyMs()).isEqualTo(25);
    }

    @Test
    void deletingPolicyAlsoDeletesItsBuckets() {
        RateLimitPolicy policy =
                new RateLimitPolicy("custom", 10, 10, 1, 20, 100, 0.1, 10, 0.2);
        service.savePolicy(policy);
        service.acquire("custom", "client", 1);

        service.deletePolicy("custom");

        assertThat(service.bucketCount()).isZero();
        assertThatThrownBy(() -> service.getPolicy("custom"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void rejectsInvalidCostBeforeCreatingBucket() {
        assertThatThrownBy(() -> service.acquire("default", "client", 101))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(service.bucketCount()).isZero();
    }
}
