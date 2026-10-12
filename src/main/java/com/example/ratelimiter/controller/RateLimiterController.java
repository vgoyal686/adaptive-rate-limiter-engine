package com.example.ratelimiter.controller;

import com.example.ratelimiter.controller.dto.AcquireRequest;
import com.example.ratelimiter.controller.dto.FeedbackRequest;
import com.example.ratelimiter.model.RateLimitDecision;
import com.example.ratelimiter.model.RateLimitPolicy;
import com.example.ratelimiter.model.RateLimitStats;
import com.example.ratelimiter.service.AdaptiveRateLimiterService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.Collection;

/**
 * Versioned HTTP boundary for enforcement, feedback, and policy administration.
 * Business rules remain in the service so this layer only maps HTTP semantics.
 */
@RestController
@RequestMapping("/api/v1")
public class RateLimiterController {
    private final AdaptiveRateLimiterService service;

    public RateLimiterController(AdaptiveRateLimiterService service) {
        this.service = service;
    }

    @PostMapping("/policies/{policyId}/acquire")
    public ResponseEntity<RateLimitDecision> acquire(
            @PathVariable String policyId,
            @Valid @RequestBody AcquireRequest request) {
        RateLimitDecision decision = service.acquire(policyId, request.key(), request.cost());
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-RateLimit-Remaining", Long.toString(decision.remainingTokens()));
        headers.set("X-RateLimit-Rate", Double.toString(decision.effectiveRatePerSecond()));

        if (!decision.allowed()) {
            // Retry-After is defined in whole seconds; round up to avoid an early retry.
            long retryAfterSeconds = Math.max(1, (decision.retryAfterMs() + 999) / 1_000);
            headers.set(HttpHeaders.RETRY_AFTER, Long.toString(retryAfterSeconds));
            return new ResponseEntity<>(decision, headers, HttpStatus.TOO_MANY_REQUESTS);
        }
        return new ResponseEntity<>(decision, headers, HttpStatus.OK);
    }

    @PostMapping("/policies/{policyId}/feedback")
    public ResponseEntity<Void> recordFeedback(
            @PathVariable String policyId,
            @Valid @RequestBody FeedbackRequest request) {
        service.recordFeedback(
                policyId, request.key(), request.latencyMs(), request.successful());
        return ResponseEntity.accepted().build();
    }

    @PutMapping("/policies/{policyId}")
    public RateLimitPolicy savePolicy(
            @PathVariable String policyId, @RequestBody RateLimitPolicy policy) {
        if (!policyId.equals(policy.id())) {
            throw new IllegalArgumentException("Path policy id must match body policy id");
        }
        return service.savePolicy(policy);
    }

    @GetMapping("/policies")
    public Collection<RateLimitPolicy> getPolicies() {
        return service.getPolicies();
    }

    @GetMapping("/policies/{policyId}")
    public RateLimitPolicy getPolicy(@PathVariable String policyId) {
        return service.getPolicy(policyId);
    }

    @DeleteMapping("/policies/{policyId}")
    public ResponseEntity<Void> deletePolicy(@PathVariable String policyId) {
        service.deletePolicy(policyId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/policies/{policyId}/stats")
    public RateLimitStats getStats(
            @PathVariable String policyId, @RequestParam String key) {
        return service.getStats(policyId, key);
    }
}
