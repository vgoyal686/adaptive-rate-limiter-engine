package com.example.ratelimiter.repository;

import com.example.ratelimiter.model.RateLimitPolicy;

import java.util.Collection;
import java.util.Optional;

/**
 * Repository contract for immutable policy definitions.
 */
public interface PolicyRepository {
    RateLimitPolicy save(RateLimitPolicy policy);

    Optional<RateLimitPolicy> findById(String id);

    Collection<RateLimitPolicy> findAll();

    boolean deleteById(String id);
}
