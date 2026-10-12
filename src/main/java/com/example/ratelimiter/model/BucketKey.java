package com.example.ratelimiter.model;

/**
 * Composite identity ensuring the same client key can be limited independently
 * by multiple policies.
 */
public record BucketKey(String policyId, String key) {
}
