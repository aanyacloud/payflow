
package com.payflow.backend;

import com.payflow.backend.exception.GlobalExceptionHandler;
import com.payflow.backend.service.RefundService;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.util.UUID;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class RefundControllerTest {

    @Mock
    private RefundService refundService;

    @InjectMocks
    private RefundController refundController;

    private MockMvc mockMvc;

    private void setUp() throws Exception {

        LocalValidatorFactoryBean validator =
                new LocalValidatorFactoryBean();

        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders
                .standaloneSetup(refundController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    // TEST 1: Reject missing Idempotency-Key header
    @Test
    void shouldRejectMissingIdempotencyKey() throws Exception {
        setUp();

        UUID paymentId = UUID.randomUUID();

        mockMvc.perform(post(
                        "/api/v1/payments/{paymentId}/refunds",
                        paymentId
                )
                        .contentType("application/json")
                        .content("""
                                {
                                    "amount": 100.00
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message")
                        .value("Required header 'Idempotency-Key' is missing"));

        verifyNoInteractions(refundService);
    }

    // TEST 2: Reject blank Idempotency-Key
    @Test
    void shouldRejectBlankIdempotencyKey() throws Exception {
        setUp();

        UUID paymentId = UUID.randomUUID();

        mockMvc.perform(post(
                        "/api/v1/payments/{paymentId}/refunds",
                        paymentId
                )
                        .header("Idempotency-Key", "   ")
                        .contentType("application/json")
                        .content("""
                                {
                                    "amount": 100.00
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verifyNoInteractions(refundService);
    }

    // TEST 3: Reject Idempotency-Key longer than 100 characters
    @Test
    void shouldRejectOversizedIdempotencyKey() throws Exception {
        setUp();

        UUID paymentId = UUID.randomUUID();
        String longKey = "a".repeat(101);

        mockMvc.perform(post(
                        "/api/v1/payments/{paymentId}/refunds",
                        paymentId
                )
                        .header("Idempotency-Key", longKey)
                        .contentType("application/json")
                        .content("""
                                {
                                    "amount": 100.00
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verifyNoInteractions(refundService);
    }
}
