package com.example.ratelimiter.repository;

import com.example.ratelimiter.model.BucketKey;
import com.example.ratelimiter.model.BucketState;
import com.example.ratelimiter.exception.CapacityExceededException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Concurrent, bounded bucket store. Reads for known keys are lock-free at the
 * repository level; only first-time bucket creation uses a short critical section.
 */
@Repository
public class InMemoryBucketRepository implements BucketRepository {
    private final ConcurrentHashMap<BucketKey, BucketState> buckets = new ConcurrentHashMap<>();
    private final int maximumBuckets;

    public InMemoryBucketRepository(
            @Value("${rate-limiter.maximum-buckets:100000}") int maximumBuckets) {
        this.maximumBuckets = maximumBuckets;
    }

    @Override
    public BucketState getOrCreate(BucketKey key, Supplier<BucketState> stateFactory) {
        BucketState existing = buckets.get(key);
        if (existing != null) {
            return existing;
        }

        // Creation is rare compared with acquisition. Serializing only this slow
        // path enforces the memory bound without adding a lock to existing keys.
        synchronized (this) {
            existing = buckets.get(key);
            if (existing != null) {
                return existing;
            }
            if (buckets.size() >= maximumBuckets) {
                throw new CapacityExceededException("In-memory bucket capacity has been reached");
            }
            BucketState created = stateFactory.get();
            buckets.put(key, created);
            return created;
        }
    }

    @Override
    public Optional<BucketState> find(BucketKey key) {
        return Optional.ofNullable(buckets.get(key));
    }

    @Override
    public Map<BucketKey, BucketState> snapshot() {
        // Return an immutable structural copy; bucket objects remain live state.
        return Map.copyOf(buckets);
    }

    @Override
    public void deleteByPolicyId(String policyId) {
        buckets.keySet().removeIf(key -> key.policyId().equals(policyId));
    }

    @Override
    public int removeIdleBefore(long cutoffNanos) {
        int sizeBefore = buckets.size();
        // ConcurrentHashMap supports weakly consistent removal during iteration.
        buckets.entrySet().removeIf(entry -> entry.getValue().lastAccessNanos() < cutoffNanos);
        return sizeBefore - buckets.size();
    }

    @Override
    public int size() {
        return buckets.size();
    }
}
