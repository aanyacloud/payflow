
package com.payflow.backend;

import com.payflow.backend.exception.GlobalExceptionHandler;
import com.payflow.backend.exception.IdempotencyConflictException;
import com.payflow.backend.exception.RefundNotFoundException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ExceptionTestController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // Verify the common error response structure.
    private ResultActions expectStandardError(
            ResultActions result,
            int status,
            String error,
            String path
    ) throws Exception {

        return result
                .andExpect(status().is(status))
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.status").value(status))
                .andExpect(jsonPath("$.error").value(error))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.path").value(path));
    }

    // Test 1: Refund not found (404).
    @Test
    void shouldReturn404WhenRefundDoesNotExist() throws Exception {

        expectStandardError(
                mockMvc.perform(get("/test/refund-not-found")),
                404,
                "Not Found",
                "/test/refund-not-found"
        ).andExpect(jsonPath("$.message")
                .value("Refund not found"));
    }

    // Test 2: Idempotency conflict (409).
    @Test
    void shouldReturn409ForIdempotencyConflict() throws Exception {

        expectStandardError(
                mockMvc.perform(get("/test/idempotency-conflict")),
                409,
                "Conflict",
                "/test/idempotency-conflict"
        ).andExpect(jsonPath("$.message")
                .value("Idempotency key already exists"));
    }

    // Test 3: Malformed JSON request (400).
    @Test
    void shouldReturn400ForMalformedJson() throws Exception {

        expectStandardError(
                mockMvc.perform(
                        post("/test/malformed-json")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{ invalid json }")
                ),
                400,
                "Bad Request",
                "/test/malformed-json"
        ).andExpect(jsonPath("$.message")
                .value("Malformed or invalid request body"));
    }

    // Test 4: Unexpected exception (500).
    @Test
    void shouldReturn500ForUnexpectedException() throws Exception {

        expectStandardError(
                mockMvc.perform(get("/test/unexpected-error")),
                500,
                "Internal Server Error",
                "/test/unexpected-error"
        ).andExpect(jsonPath("$.message")
                .value("An unexpected error occurred"));
    }

    // Minimal test-only controller to trigger exceptions.
    @RestController
    static class ExceptionTestController {

        @GetMapping("/test/refund-not-found")
        public void refundNotFound() {
            throw new RefundNotFoundException("Refund not found");
        }

        @GetMapping("/test/idempotency-conflict")
        public void idempotencyConflict() {
            throw new IdempotencyConflictException(
                    "Idempotency key already exists"
            );
        }

        @PostMapping(
                value = "/test/malformed-json",
                consumes = MediaType.APPLICATION_JSON_VALUE
        )
        public Map<String, Object> malformedJson(
                @RequestBody Map<String, Object> body
        ) {
            return body;
        }

        @GetMapping("/test/unexpected-error")
        public void unexpectedError() {
            throw new RuntimeException("Internal database details");
        }
    }
}
