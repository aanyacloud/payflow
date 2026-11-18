
package com.payflow.backend;

import com.payflow.backend.entity.Payment;
import com.payflow.backend.entity.PaymentStatus;
import com.payflow.backend.exception.GlobalExceptionHandler;
import com.payflow.backend.exception.InvalidPaymentStatusTransitionException;
import com.payflow.backend.exception.PaymentNotFoundException;
import com.payflow.backend.service.PaymentService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.math.BigDecimal;
import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class PaymentStatusControllerTest {

    @Mock
    private PaymentService paymentService;

    @InjectMocks
    private PaymentController paymentController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {

        LocalValidatorFactoryBean validator =
                new LocalValidatorFactoryBean();

        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders
                .standaloneSetup(paymentController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    // Test 1: Successfully update PENDING to PROCESSING.
    @Test
    void shouldUpdatePaymentStatusSuccessfully() throws Exception {

        UUID paymentId = UUID.randomUUID();

        Payment processingPayment = new Payment(
                new BigDecimal("799.99"),
                "INR",
                PaymentStatus.PROCESSING.name()
        );

        when(paymentService.updatePaymentStatus(
                paymentId,
                PaymentStatus.PROCESSING
        )).thenReturn(processingPayment);

        mockMvc.perform(
                patch("/api/v1/payments/{id}/status", paymentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "status": "PROCESSING"
                                }
                                """)
        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PROCESSING"))
                .andExpect(jsonPath("$.amount").value(799.99))
                .andExpect(jsonPath("$.currency").value("INR"));

        verify(paymentService).updatePaymentStatus(
                paymentId,
                PaymentStatus.PROCESSING
        );
    }

    // Test 2: Reject an invalid payment status transition.
    @Test
    void shouldReturn409ForInvalidStatusTransition() throws Exception {

        UUID paymentId = UUID.randomUUID();

        when(paymentService.updatePaymentStatus(
                paymentId,
                PaymentStatus.SUCCESS
        )).thenThrow(
                new InvalidPaymentStatusTransitionException(
                        "Invalid payment status transition: PENDING -> SUCCESS"
                )
        );

        mockMvc.perform(
                patch("/api/v1/payments/{id}/status", paymentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "status": "SUCCESS"
                                }
                                """)
        )
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value(
                        "Invalid payment status transition: PENDING -> SUCCESS"
                ));
    }

    // Test 3: Return 404 when the payment does not exist.
    @Test
    void shouldReturn404WhenPaymentDoesNotExist() throws Exception {

        UUID paymentId = UUID.randomUUID();

        when(paymentService.updatePaymentStatus(
                paymentId,
                PaymentStatus.PROCESSING
        )).thenThrow(
                new PaymentNotFoundException(
                        "Payment not found: " + paymentId
                )
        );

        mockMvc.perform(
                patch("/api/v1/payments/{id}/status", paymentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "status": "PROCESSING"
                                }
                                """)
        )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"));
    }

    // Test 4: Reject an unknown status value.
    @Test
    void shouldReturn400ForInvalidStatusValue() throws Exception {

        UUID paymentId = UUID.randomUUID();

        mockMvc.perform(
                patch("/api/v1/payments/{id}/status", paymentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "status": "UNKNOWN"
                                }
                                """)
        )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(paymentService);
    }

    // Test 5: Reject a null status value.
    @Test
    void shouldReturn400WhenStatusIsNull() throws Exception {

        UUID paymentId = UUID.randomUUID();

        mockMvc.perform(
                patch("/api/v1/payments/{id}/status", paymentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "status": null
                                }
                                """)
        )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"));

        verifyNoInteractions(paymentService);
    }
}
