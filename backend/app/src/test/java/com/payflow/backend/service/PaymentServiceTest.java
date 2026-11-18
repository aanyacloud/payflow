
package com.payflow.backend.service;

import com.payflow.backend.entity.Payment;
import com.payflow.backend.exception.PaymentNotFoundException;
import com.payflow.backend.repository.PaymentRepository;
import com.payflow.backend.repository.PaymentStatusHistoryRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentStatusHistoryRepository historyRepository;

    @InjectMocks
    private PaymentService paymentService;

    private UUID paymentId;

    @BeforeEach
    void setUp() {
        paymentId = UUID.randomUUID();
    }

    // Test 1: Successfully create a payment.
    @Test
    void shouldCreatePaymentSuccessfully() {

        BigDecimal amount = new BigDecimal("499.99");

        when(paymentRepository.save(any(Payment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Payment result = paymentService.createPayment(amount, "INR");

        assertNotNull(result);
        assertEquals(amount, result.getAmount());
        assertEquals("INR", result.getCurrency());
        assertEquals("PENDING", result.getStatus());

        verify(paymentRepository).save(any(Payment.class));
    }

    // Test 2: Successfully retrieve a payment.
    @Test
    void shouldRetrievePaymentSuccessfully() {

        Payment payment = new Payment(
                new BigDecimal("499.99"),
                "INR",
                "PENDING"
        );

        when(paymentRepository.findById(paymentId))
                .thenReturn(Optional.of(payment));

        Payment result = paymentService.getPaymentById(paymentId);

        assertNotNull(result);
        assertEquals(new BigDecimal("499.99"), result.getAmount());
        assertEquals("INR", result.getCurrency());

        verify(paymentRepository).findById(paymentId);
    }

    // Test 3: Throw exception when payment does not exist.
    @Test
    void shouldThrowExceptionWhenPaymentNotFound() {

        when(paymentRepository.findById(paymentId))
                .thenReturn(Optional.empty());

        assertThrows(
                PaymentNotFoundException.class,
                () -> paymentService.getPaymentById(paymentId)
        );

        verify(paymentRepository).findById(paymentId);
    }
}
