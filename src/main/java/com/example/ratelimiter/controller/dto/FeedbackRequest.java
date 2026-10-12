package com.example.ratelimiter.controller.dto;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

/**
 * Observed downstream result used to tune the key's future refill rate.
 */
public record FeedbackRequest(
        @NotBlank @Size(max = 256) String key,
        @Min(0) long latencyMs,
        boolean successful) {
}
