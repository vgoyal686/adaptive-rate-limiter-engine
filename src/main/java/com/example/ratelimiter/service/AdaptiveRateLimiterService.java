package com.example.ratelimiter.service;

import com.example.ratelimiter.config.RateLimiterProperties;
import com.example.ratelimiter.exception.ResourceNotFoundException;
import com.example.ratelimiter.model.BucketKey;
import com.example.ratelimiter.model.BucketState;
import com.example.ratelimiter.model.RateLimitDecision;
import com.example.ratelimiter.model.RateLimitPolicy;
import com.example.ratelimiter.model.RateLimitStats;
import com.example.ratelimiter.repository.BucketRepository;
import com.example.ratelimiter.repository.PolicyRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.Collection;

/**
 * Application service coordinating policies and per-client token buckets.
 *
 * <p>The acquire path performs no I/O: it resolves an in-memory policy, locates
 * the bucket, and delegates the atomic decision to that bucket's state.</p>
 */
@Service
public class AdaptiveRateLimiterService {
    private final PolicyRepository policyRepository;
    private final BucketRepository bucketRepository;
    private final RateLimiterProperties properties;

    public AdaptiveRateLimiterService(
            PolicyRepository policyRepository,
            BucketRepository bucketRepository,
            RateLimiterProperties properties) {
        this.policyRepository = policyRepository;
        this.bucketRepository = bucketRepository;
        this.properties = properties;
    }

    @PostConstruct
    void initializeDefaultPolicy() {
        // The default keeps the API usable without a separate provisioning step.
        policyRepository.save(properties.getDefaultPolicy().toPolicy());
    }

    /**
     * Attempts to consume {@code cost} tokens for one policy/client pair.
     */
    public RateLimitDecision acquire(String policyId, String key, int cost) {
        RateLimitPolicy policy = getPolicy(policyId);
        validateKeyAndCost(key, cost, policy);
        BucketKey bucketKey = new BucketKey(policyId, key);
        long now = System.nanoTime();
        BucketState state = bucketRepository.getOrCreate(
                bucketKey, () -> new BucketState(policy, now));
        return state.tryAcquire(policy, cost, now);
    }

    public void recordFeedback(
            String policyId, String key, long latencyMs, boolean successful) {
        if (latencyMs < 0) {
            throw new IllegalArgumentException("Latency must not be negative");
        }
        RateLimitPolicy policy = getPolicy(policyId);
        validateKey(key);
        BucketKey bucketKey = new BucketKey(policyId, key);
        long now = System.nanoTime();
        BucketState state = bucketRepository.getOrCreate(
                bucketKey, () -> new BucketState(policy, now));
        state.recordFeedback(policy, latencyMs, successful, now);
    }

    public RateLimitStats getStats(String policyId, String key) {
        RateLimitPolicy policy = getPolicy(policyId);
        validateKey(key);
        BucketKey bucketKey = new BucketKey(policyId, key);
        BucketState state = bucketRepository.find(bucketKey)
                .orElseThrow(() -> new ResourceNotFoundException("Bucket not found"));
        return state.snapshot(bucketKey, policy, System.nanoTime());
    }

    public RateLimitPolicy savePolicy(RateLimitPolicy policy) {
        return policyRepository.save(policy);
    }

    public RateLimitPolicy getPolicy(String id) {
        return policyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Policy not found: " + id));
    }

    public Collection<RateLimitPolicy> getPolicies() {
        return policyRepository.findAll();
    }

    public void deletePolicy(String id) {
        if ("default".equals(id)) {
            throw new IllegalArgumentException("The default policy cannot be deleted");
        }
        if (!policyRepository.deleteById(id)) {
            throw new ResourceNotFoundException("Policy not found: " + id);
        }
        bucketRepository.deleteByPolicyId(id);
    }

    public int bucketCount() {
        return bucketRepository.size();
    }

    @Scheduled(fixedDelayString = "${rate-limiter.cleanup-interval-ms:60000}")
    public void evictIdleBuckets() {
        // nanoTime is monotonic and therefore safe from wall-clock adjustments.
        long cutoff = System.nanoTime() - properties.getIdleTtl().toNanos();
        bucketRepository.removeIdleBefore(cutoff);
    }

    private void validateKeyAndCost(String key, int cost, RateLimitPolicy policy) {
        validateKey(key);
        if (cost <= 0 || cost > policy.capacity()) {
            throw new IllegalArgumentException(
                    "Cost must be between 1 and the policy capacity");
        }
    }

    private void validateKey(String key) {
        if (key == null || key.isBlank() || key.length() > 256) {
            throw new IllegalArgumentException("Key must contain 1 to 256 characters");
        }
    }
}
