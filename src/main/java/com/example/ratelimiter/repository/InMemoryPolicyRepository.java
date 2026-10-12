package com.example.ratelimiter.repository;

import com.example.ratelimiter.model.RateLimitPolicy;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe policy store. Policies are immutable, so readers can safely use
 * returned instances without defensive copies.
 */
@Repository
public class InMemoryPolicyRepository implements PolicyRepository {
    private final ConcurrentHashMap<String, RateLimitPolicy> policies = new ConcurrentHashMap<>();

    @Override
    public RateLimitPolicy save(RateLimitPolicy policy) {
        policies.put(policy.id(), policy);
        return policy;
    }

    @Override
    public Optional<RateLimitPolicy> findById(String id) {
        return Optional.ofNullable(policies.get(id));
    }

    @Override
    public Collection<RateLimitPolicy> findAll() {
        // Isolate callers from later structural changes to the backing map.
        return List.copyOf(policies.values());
    }

    @Override
    public boolean deleteById(String id) {
        return policies.remove(id) != null;
    }
}
