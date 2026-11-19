
package com.payflow.backend.service;

import com.payflow.backend.entity.OutboxEvent;
import com.payflow.backend.entity.Payment;
import com.payflow.backend.entity.User;
import com.payflow.backend.events.PaymentCreatedEvent;
import com.payflow.backend.exception.PaymentNotFoundException;
import com.payflow.backend.repository.OutboxEventRepository;
import com.payflow.backend.repository.PaymentRepository;
import com.payflow.backend.repository.PaymentStatusHistoryRepository;
import com.payflow.backend.repository.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentStatusHistoryRepository historyRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private PaymentService paymentService;

    private UUID userId;
    private UUID paymentId;
    private User user;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        paymentId = UUID.randomUUID();

        user = new User(
                "payment-test@payflow.com",
                "password",
                "USER"
        );
    }

    @Test
    void shouldCreatePaymentSuccessfully() throws Exception {

        BigDecimal amount = new BigDecimal("499.99");
        String idempotencyKey = "test-payment-001";

        Payment payment = new Payment(
                user,
                amount,
                "INR",
                "PENDING",
                idempotencyKey
        );

        when(userRepository.findById(userId))
                .thenReturn(Optional.of(user));

        when(paymentRepository.insertIfAbsent(
                any(UUID.class),
                eq(userId),
                eq(amount),
                eq("INR"),
                eq("PENDING"),
                any(Instant.class),
                eq(idempotencyKey)
        )).thenReturn(1);

        when(paymentRepository.findByUser_IdAndIdempotencyKey(
                userId,
                idempotencyKey
        )).thenReturn(Optional.of(payment));

        when(objectMapper.writeValueAsString(
                any(PaymentCreatedEvent.class)
        )).thenReturn("{\"eventType\":\"PAYMENT_CREATED\"}");

        Payment result = paymentService.createPayment(
                userId,
                amount,
                "INR",
                idempotencyKey
        );

        assertNotNull(result);
        assertEquals(amount, result.getAmount());
        assertEquals("INR", result.getCurrency());
        assertEquals("PENDING", result.getStatus());
        assertEquals(idempotencyKey, result.getIdempotencyKey());

        verify(userRepository).findById(userId);

        ArgumentCaptor<OutboxEvent> eventCaptor =
                ArgumentCaptor.forClass(OutboxEvent.class);

        verify(outboxEventRepository).save(eventCaptor.capture());

        OutboxEvent savedEvent = eventCaptor.getValue();

        assertNotNull(savedEvent.eventId());
        assertEquals("PAYMENT", savedEvent.aggregateType());
        assertEquals("PAYMENT_CREATED", savedEvent.eventType());
        assertEquals(payment.getId(), savedEvent.aggregateId());
        assertEquals("{\"eventType\":\"PAYMENT_CREATED\"}",
                savedEvent.payload());
        assertEquals(0, savedEvent.attempts());
        assertNull(savedEvent.publishedAt());
    }

    @Test
    void shouldNotCreateDuplicateOutboxEventForIdempotentRetry() {

        BigDecimal amount = new BigDecimal("499.99");
        String idempotencyKey = "test-payment-001";

        Payment existingPayment = new Payment(
                user,
                amount,
                "INR",
                "PENDING",
                idempotencyKey
        );

        when(userRepository.findById(userId))
                .thenReturn(Optional.of(user));

        when(paymentRepository.insertIfAbsent(
                any(UUID.class),
                eq(userId),
                eq(amount),
                eq("INR"),
                eq("PENDING"),
                any(Instant.class),
                eq(idempotencyKey)
        )).thenReturn(0);

        when(paymentRepository.findByUser_IdAndIdempotencyKey(
                userId,
                idempotencyKey
        )).thenReturn(Optional.of(existingPayment));

        Payment result = paymentService.createPayment(
                userId,
                amount,
                "INR",
                idempotencyKey
        );

        assertNotNull(result);
        assertEquals(amount, result.getAmount());
        assertEquals("INR", result.getCurrency());

        verify(outboxEventRepository, never())
                .save(any(OutboxEvent.class));

        verifyNoInteractions(objectMapper);
    }

    @Test
    void shouldRetrievePaymentSuccessfully() {

        Payment payment = new Payment(
                user,
                new BigDecimal("499.99"),
                "INR",
                "PENDING"
        );

        when(paymentRepository.findByIdAndUser_Id(paymentId, userId))
                .thenReturn(Optional.of(payment));

        Payment result = paymentService.getPaymentById(userId, paymentId);

        assertNotNull(result);
        assertEquals(new BigDecimal("499.99"), result.getAmount());
        assertEquals("INR", result.getCurrency());

        verify(paymentRepository)
                .findByIdAndUser_Id(paymentId, userId);
    }

    @Test
    void shouldThrowExceptionWhenPaymentNotFound() {

        when(paymentRepository.findByIdAndUser_Id(paymentId, userId))
                .thenReturn(Optional.empty());

        assertThrows(
                PaymentNotFoundException.class,
                () -> paymentService.getPaymentById(userId, paymentId)
        );

        verify(paymentRepository)
                .findByIdAndUser_Id(paymentId, userId);
    }
}
