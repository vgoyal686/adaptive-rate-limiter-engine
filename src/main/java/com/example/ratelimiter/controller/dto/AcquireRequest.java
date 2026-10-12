package com.example.ratelimiter.controller.dto;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

/**
 * Request to consume a weighted number of tokens for a client key.
 */
public record AcquireRequest(
        @NotBlank @Size(max = 256) String key,
        @Min(1) @Max(1_000_000) int cost) {
}
