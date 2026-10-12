package com.example.ratelimiter.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "rate-limiter.default-policy.initial-rate-per-second=1",
        "rate-limiter.default-policy.minimum-rate-per-second=1",
        "rate-limiter.default-policy.maximum-rate-per-second=1"
})
@AutoConfigureMockMvc
class RateLimiterControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void acquireReturnsRateLimitHeadersAndRejectsExcessTraffic() throws Exception {
        String body = "{\"key\":\"controller-client\",\"cost\":100}";
        mockMvc.perform(post("/api/v1/policies/default/acquire")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(header().string("X-RateLimit-Remaining", "0"))
                .andExpect(jsonPath("$.allowed").value(true));

        mockMvc.perform(post("/api/v1/policies/default/acquire")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"key\":\"controller-client\",\"cost\":1}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.allowed").value(false));
    }

    @Test
    void validatesRequestAndReturnsStructuredError() throws Exception {
        mockMvc.perform(post("/api/v1/policies/default/acquire")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"key\":\"\",\"cost\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void exposesDefaultPolicy() throws Exception {
        mockMvc.perform(get("/api/v1/policies/default"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("default"))
                .andExpect(jsonPath("$.capacity").value(100));
    }
}
