
package com.payflow.backend.service;

import com.payflow.backend.entity.Payment;
import com.payflow.backend.entity.PaymentStatus;
import com.payflow.backend.entity.PaymentStatusHistory;
import com.payflow.backend.entity.User;
import com.payflow.backend.exception.InvalidPaymentStatusTransitionException;
import com.payflow.backend.exception.PaymentNotFoundException;
import com.payflow.backend.repository.PaymentRepository;
import com.payflow.backend.repository.PaymentStatusHistoryRepository;
import com.payflow.backend.repository.UserRepository;

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
class PaymentServiceStatusTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentStatusHistoryRepository historyRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private PaymentService paymentService;

    private final UUID userId = UUID.randomUUID();
    private final UUID paymentId = UUID.randomUUID();

    private User user;

    @BeforeEach
    void setUp() {
        user = new User(
                "status-test@payflow.com",
                "password",
                "USER"
        );
    }

    private Payment createPayment(PaymentStatus status) {
        return new Payment(
                user,
                new BigDecimal("799.99"),
                "INR",
                status.name()
        );
    }

    // TEST 1: PENDING -> PROCESSING
    @Test
    void shouldUpdatePendingToProcessing() {

        Payment pending = createPayment(PaymentStatus.PENDING);
        Payment processing = createPayment(PaymentStatus.PROCESSING);

        when(paymentRepository.findByIdAndUser_Id(paymentId, userId))
                .thenReturn(Optional.of(pending), Optional.of(processing));

        when(paymentRepository.updateStatusIfCurrent(
                paymentId,
                userId,
                "PENDING",
                "PROCESSING"
        )).thenReturn(1);

        Payment result = paymentService.updatePaymentStatus(
                userId,
                paymentId,
                PaymentStatus.PROCESSING
        );

        assertEquals("PROCESSING", result.getStatus());

        verify(paymentRepository).updateStatusIfCurrent(
                paymentId,
                userId,
                "PENDING",
                "PROCESSING"
        );

        verify(historyRepository).save(any(PaymentStatusHistory.class));
    }

    // TEST 2: PROCESSING -> SUCCESS
    @Test
    void shouldUpdateProcessingToSuccess() {

        Payment processing = createPayment(PaymentStatus.PROCESSING);
        Payment success = createPayment(PaymentStatus.SUCCESS);

        when(paymentRepository.findByIdAndUser_Id(paymentId, userId))
                .thenReturn(Optional.of(processing), Optional.of(success));

        when(paymentRepository.updateStatusIfCurrent(
                paymentId,
                userId,
                "PROCESSING",
                "SUCCESS"
        )).thenReturn(1);

        Payment result = paymentService.updatePaymentStatus(
                userId,
                paymentId,
                PaymentStatus.SUCCESS
        );

        assertEquals("SUCCESS", result.getStatus());

        verify(historyRepository).save(any(PaymentStatusHistory.class));
    }

    // TEST 3: PROCESSING -> FAILED
    @Test
    void shouldUpdateProcessingToFailed() {

        Payment processing = createPayment(PaymentStatus.PROCESSING);
        Payment failed = createPayment(PaymentStatus.FAILED);

        when(paymentRepository.findByIdAndUser_Id(paymentId, userId))
                .thenReturn(Optional.of(processing), Optional.of(failed));

        when(paymentRepository.updateStatusIfCurrent(
                paymentId,
                userId,
                "PROCESSING",
                "FAILED"
        )).thenReturn(1);

        Payment result = paymentService.updatePaymentStatus(
                userId,
                paymentId,
                PaymentStatus.FAILED
        );

        assertEquals("FAILED", result.getStatus());

        verify(historyRepository).save(any(PaymentStatusHistory.class));
    }

    // TEST 4: Reject PENDING -> SUCCESS
    @Test
    void shouldRejectPendingDirectlyToSuccess() {

        Payment pending = createPayment(PaymentStatus.PENDING);

        when(paymentRepository.findByIdAndUser_Id(paymentId, userId))
                .thenReturn(Optional.of(pending));

        assertThrows(
                InvalidPaymentStatusTransitionException.class,
                () -> paymentService.updatePaymentStatus(
                        userId,
                        paymentId,
                        PaymentStatus.SUCCESS
                )
        );

        verify(paymentRepository, never())
                .updateStatusIfCurrent(any(), any(), anyString(), anyString());

        verify(historyRepository, never())
                .save(any(PaymentStatusHistory.class));
    }

    // TEST 5: Reject SUCCESS -> FAILED
    @Test
    void shouldRejectSuccessToFailed() {

        Payment success = createPayment(PaymentStatus.SUCCESS);

        when(paymentRepository.findByIdAndUser_Id(paymentId, userId))
                .thenReturn(Optional.of(success));

        assertThrows(
                InvalidPaymentStatusTransitionException.class,
                () -> paymentService.updatePaymentStatus(
                        userId,
                        paymentId,
                        PaymentStatus.FAILED
                )
        );

        verify(paymentRepository, never())
                .updateStatusIfCurrent(any(), any(), anyString(), anyString());

        verify(historyRepository, never())
                .save(any(PaymentStatusHistory.class));
    }

    // TEST 6: Reject FAILED -> SUCCESS
    @Test
    void shouldRejectFailedToSuccess() {

        Payment failed = createPayment(PaymentStatus.FAILED);

        when(paymentRepository.findByIdAndUser_Id(paymentId, userId))
                .thenReturn(Optional.of(failed));

        assertThrows(
                InvalidPaymentStatusTransitionException.class,
                () -> paymentService.updatePaymentStatus(
                        userId,
                        paymentId,
                        PaymentStatus.SUCCESS
                )
        );

        verify(paymentRepository, never())
                .updateStatusIfCurrent(any(), any(), anyString(), anyString());

        verify(historyRepository, never())
                .save(any(PaymentStatusHistory.class));
    }

    // TEST 7: Payment does not exist
    @Test
    void shouldThrowExceptionWhenPaymentDoesNotExist() {

        when(paymentRepository.findByIdAndUser_Id(paymentId, userId))
                .thenReturn(Optional.empty());

        assertThrows(
                PaymentNotFoundException.class,
                () -> paymentService.updatePaymentStatus(
                        userId,
                        paymentId,
                        PaymentStatus.PROCESSING
                )
        );

        verify(paymentRepository, never())
                .updateStatusIfCurrent(any(), any(), anyString(), anyString());

        verify(historyRepository, never())
                .save(any(PaymentStatusHistory.class));
    }

    // TEST 8: Concurrent payment status change
    @Test
    void shouldRejectUpdateWhenPaymentChangesConcurrently() {

        Payment pending = createPayment(PaymentStatus.PENDING);

        when(paymentRepository.findByIdAndUser_Id(paymentId, userId))
                .thenReturn(Optional.of(pending));

        when(paymentRepository.updateStatusIfCurrent(
                paymentId,
                userId,
                "PENDING",
                "PROCESSING"
        )).thenReturn(0);

        assertThrows(
                InvalidPaymentStatusTransitionException.class,
                () -> paymentService.updatePaymentStatus(
                        userId,
                        paymentId,
                        PaymentStatus.PROCESSING
                )
        );

        verify(historyRepository, never())
                .save(any(PaymentStatusHistory.class));
    }
}
