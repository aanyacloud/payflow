
package com.payflow.backend;

import com.payflow.backend.entity.Payment;
import com.payflow.backend.entity.User;
import com.payflow.backend.exception.GlobalExceptionHandler;
import com.payflow.backend.exception.PaymentNotFoundException;
import com.payflow.backend.service.PaymentService;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;

import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;

import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class PaymentControllerTest {

    @Mock
    private PaymentService paymentService;

    @InjectMocks
    private PaymentController paymentController;

    private MockMvc mockMvc;

    private UUID userId;

    private User user;

    private void setUp() throws Exception {

        userId = UUID.randomUUID();

        user = new User(
                "payment-test@payflow.com",
                "password",
                "USER"
        );

        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .subject(userId.toString())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(jwt)
        );

        LocalValidatorFactoryBean validator =
                new LocalValidatorFactoryBean();

        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders
                .standaloneSetup(paymentController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(
                        new AuthenticationPrincipalArgumentResolver(),
                        new PageableHandlerMethodArgumentResolver()
                )
                .setValidator(validator)
                .build();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // GET TEST 1: Retrieve payment by ID
    @Test
    void shouldReturnPaymentById() throws Exception {

        setUp();

        UUID id = UUID.randomUUID();

        Payment payment = new Payment(
                user,
                new BigDecimal("499.99"),
                "INR",
                "PENDING"
        );

        when(paymentService.getPaymentById(userId, id))
                .thenReturn(payment);

        mockMvc.perform(get("/api/v1/payments/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(499.99))
                .andExpect(jsonPath("$.currency").value("INR"))
                .andExpect(jsonPath("$.status").value("PENDING"));

        verify(paymentService).getPaymentById(userId, id);
    }

    // GET TEST 2: Payment not found
    @Test
    void shouldReturn404WhenPaymentDoesNotExist() throws Exception {

        setUp();

        UUID id = UUID.randomUUID();

        when(paymentService.getPaymentById(userId, id))
                .thenThrow(new PaymentNotFoundException(
                        "Payment not found: " + id
                ));

        mockMvc.perform(get("/api/v1/payments/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"));
    }

    // GET TEST 3: Paginated payments
    @Test
    void shouldReturnPaginatedPayments() throws Exception {

        setUp();

        Payment payment = new Payment(
                user,
                new BigDecimal("499.99"),
                "INR",
                "PENDING"
        );

        Page<Payment> page = new PageImpl<>(
                List.of(payment),
                PageRequest.of(
                        0,
                        10,
                        Sort.by(Sort.Direction.DESC, "createdAt")
                ),
                1
        );

        when(paymentService.getPayments(
                eq(userId),
                any()
        )).thenReturn(page);

        mockMvc.perform(get("/api/v1/payments")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].currency").value("INR"));

        verify(paymentService).getPayments(eq(userId), any());
    }

    // POST TEST 1: Successful payment creation
    @Test
    void shouldCreatePaymentSuccessfully() throws Exception {

        setUp();

        Payment payment = new Payment(
                user,
                new BigDecimal("499.99"),
                "INR",
                "PENDING",
                "test-payment-001"
        );

        when(paymentService.createPayment(
                userId,
                new BigDecimal("499.99"),
                "INR",
                "test-payment-001"
        )).thenReturn(payment);

        mockMvc.perform(post("/api/v1/payments")
                        .header("Idempotency-Key", "test-payment-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "amount": 499.99,
                                    "currency": "INR"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amount").value(499.99))
                .andExpect(jsonPath("$.currency").value("INR"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.idempotencyKey")
                        .value("test-payment-001"));

        verify(paymentService).createPayment(
                userId,
                new BigDecimal("499.99"),
                "INR",
                "test-payment-001"
        );
    }

    // POST TEST 2: Reject zero amount
    @Test
    void shouldRejectZeroAmount() throws Exception {

        setUp();

        mockMvc.perform(post("/api/v1/payments")
                        .header("Idempotency-Key", "test-zero-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "amount": 0,
                                    "currency": "INR"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"));

        verifyNoInteractions(paymentService);
    }

    // POST TEST 3: Reject invalid currency
    @Test
    void shouldRejectInvalidCurrency() throws Exception {

        setUp();

        mockMvc.perform(post("/api/v1/payments")
                        .header("Idempotency-Key", "test-currency-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "amount": 499.99,
                                    "currency": "inr"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verifyNoInteractions(paymentService);
    }

    // POST TEST 4: Reject missing amount
    @Test
    void shouldRejectMissingAmount() throws Exception {

        setUp();

        mockMvc.perform(post("/api/v1/payments")
                        .header("Idempotency-Key", "test-missing-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "currency": "INR"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verifyNoInteractions(paymentService);
    }

    // POST TEST 5: Reject missing Idempotency-Key header
    @Test
    void shouldRejectMissingIdempotencyKey() throws Exception {

        setUp();

        mockMvc.perform(post("/api/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "amount": 499.99,
                                    "currency": "INR"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message")
                        .value("Required header 'Idempotency-Key' is missing"));

        verifyNoInteractions(paymentService);
    }

    // POST TEST 6: Reject blank Idempotency-Key
    @Test
    void shouldRejectBlankIdempotencyKey() throws Exception {

        setUp();

        mockMvc.perform(post("/api/v1/payments")
                        .header("Idempotency-Key", "   ")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "amount": 499.99,
                                    "currency": "INR"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verifyNoInteractions(paymentService);
    }

    // POST TEST 7: Reject Idempotency-Key longer than 100 characters
    @Test
    void shouldRejectOversizedIdempotencyKey() throws Exception {

        setUp();

        String longKey = "a".repeat(101);

        mockMvc.perform(post("/api/v1/payments")
                        .header("Idempotency-Key", longKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "amount": 499.99,
                                    "currency": "INR"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verifyNoInteractions(paymentService);
    }
}
