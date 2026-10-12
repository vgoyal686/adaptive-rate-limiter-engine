package com.example.ratelimiter.repository;

import com.example.ratelimiter.model.BucketKey;
import com.example.ratelimiter.model.BucketState;

import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Storage abstraction for mutable bucket state. The current implementation is
 * intentionally in-memory, while this contract keeps storage concerns out of
 * the rate-limiting service.
 */
public interface BucketRepository {
    /**
     * Returns an existing state or creates it lazily. The supplier prevents
     * unnecessary allocation on the common existing-bucket path.
     */
    BucketState getOrCreate(BucketKey key, Supplier<BucketState> stateFactory);

    Optional<BucketState> find(BucketKey key);

    Map<BucketKey, BucketState> snapshot();

    void deleteByPolicyId(String policyId);

    int removeIdleBefore(long cutoffNanos);

    int size();
}
