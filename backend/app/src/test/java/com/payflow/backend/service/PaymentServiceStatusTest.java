
package com.payflow.backend.service;

import com.payflow.backend.entity.Payment;
import com.payflow.backend.entity.PaymentStatus;
import com.payflow.backend.entity.PaymentStatusHistory;
import com.payflow.backend.exception.InvalidPaymentStatusTransitionException;
import com.payflow.backend.exception.PaymentNotFoundException;
import com.payflow.backend.repository.PaymentRepository;
import com.payflow.backend.repository.PaymentStatusHistoryRepository;

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

    @InjectMocks
    private PaymentService paymentService;

    private Payment createPayment(PaymentStatus status) {
        return new Payment(
                new BigDecimal("799.99"),
                "INR",
                status.name()
        );
    }

    // 1. PENDING -> PROCESSING
    @Test
    void shouldUpdatePendingToProcessing() {

        UUID paymentId = UUID.randomUUID();

        Payment pending = createPayment(PaymentStatus.PENDING);
        Payment processing = createPayment(PaymentStatus.PROCESSING);

        when(paymentRepository.findById(paymentId))
                .thenReturn(
                        Optional.of(pending),
                        Optional.of(processing)
                );

        when(paymentRepository.updateStatusIfCurrent(
                paymentId,
                "PENDING",
                "PROCESSING"
        )).thenReturn(1);

        Payment result = paymentService.updatePaymentStatus(
                paymentId,
                PaymentStatus.PROCESSING
        );

        assertEquals("PROCESSING", result.getStatus());

        verify(paymentRepository).updateStatusIfCurrent(
                paymentId,
                "PENDING",
                "PROCESSING"
        );

        verify(historyRepository).save(
                any(PaymentStatusHistory.class)
        );
    }

    // 2. PROCESSING -> SUCCESS
    @Test
    void shouldUpdateProcessingToSuccess() {

        UUID paymentId = UUID.randomUUID();

        Payment processing = createPayment(PaymentStatus.PROCESSING);
        Payment success = createPayment(PaymentStatus.SUCCESS);

        when(paymentRepository.findById(paymentId))
                .thenReturn(
                        Optional.of(processing),
                        Optional.of(success)
                );

        when(paymentRepository.updateStatusIfCurrent(
                paymentId,
                "PROCESSING",
                "SUCCESS"
        )).thenReturn(1);

        Payment result = paymentService.updatePaymentStatus(
                paymentId,
                PaymentStatus.SUCCESS
        );

        assertEquals("SUCCESS", result.getStatus());

        verify(historyRepository).save(
                any(PaymentStatusHistory.class)
        );
    }

    // 3. PROCESSING -> FAILED
    @Test
    void shouldUpdateProcessingToFailed() {

        UUID paymentId = UUID.randomUUID();

        Payment processing = createPayment(PaymentStatus.PROCESSING);
        Payment failed = createPayment(PaymentStatus.FAILED);

        when(paymentRepository.findById(paymentId))
                .thenReturn(
                        Optional.of(processing),
                        Optional.of(failed)
                );

        when(paymentRepository.updateStatusIfCurrent(
                paymentId,
                "PROCESSING",
                "FAILED"
        )).thenReturn(1);

        Payment result = paymentService.updatePaymentStatus(
                paymentId,
                PaymentStatus.FAILED
        );

        assertEquals("FAILED", result.getStatus());

        verify(historyRepository).save(
                any(PaymentStatusHistory.class)
        );
    }

    // 4. Reject PENDING -> SUCCESS
    @Test
    void shouldRejectPendingDirectlyToSuccess() {

        UUID paymentId = UUID.randomUUID();

        Payment pending = createPayment(PaymentStatus.PENDING);

        when(paymentRepository.findById(paymentId))
                .thenReturn(Optional.of(pending));

        assertThrows(
                InvalidPaymentStatusTransitionException.class,
                () -> paymentService.updatePaymentStatus(
                        paymentId,
                        PaymentStatus.SUCCESS
                )
        );

        verify(paymentRepository, never())
                .updateStatusIfCurrent(
                        any(),
                        anyString(),
                        anyString()
                );

        verify(historyRepository, never())
                .save(any(PaymentStatusHistory.class));
    }

    // 5. Reject SUCCESS -> FAILED
    @Test
    void shouldRejectSuccessToFailed() {

        UUID paymentId = UUID.randomUUID();

        Payment success = createPayment(PaymentStatus.SUCCESS);

        when(paymentRepository.findById(paymentId))
                .thenReturn(Optional.of(success));

        assertThrows(
                InvalidPaymentStatusTransitionException.class,
                () -> paymentService.updatePaymentStatus(
                        paymentId,
                        PaymentStatus.FAILED
                )
        );

        verify(paymentRepository, never())
                .updateStatusIfCurrent(
                        any(),
                        anyString(),
                        anyString()
                );

        verify(historyRepository, never())
                .save(any(PaymentStatusHistory.class));
    }

    // 6. Reject FAILED -> SUCCESS
    @Test
    void shouldRejectFailedToSuccess() {

        UUID paymentId = UUID.randomUUID();

        Payment failed = createPayment(PaymentStatus.FAILED);

        when(paymentRepository.findById(paymentId))
                .thenReturn(Optional.of(failed));

        assertThrows(
                InvalidPaymentStatusTransitionException.class,
                () -> paymentService.updatePaymentStatus(
                        paymentId,
                        PaymentStatus.SUCCESS
                )
        );

        verify(paymentRepository, never())
                .updateStatusIfCurrent(
                        any(),
                        anyString(),
                        anyString()
                );

        verify(historyRepository, never())
                .save(any(PaymentStatusHistory.class));
    }

    // 7. Payment does not exist
    @Test
    void shouldThrowExceptionWhenPaymentDoesNotExist() {

        UUID paymentId = UUID.randomUUID();

        when(paymentRepository.findById(paymentId))
                .thenReturn(Optional.empty());

        assertThrows(
                PaymentNotFoundException.class,
                () -> paymentService.updatePaymentStatus(
                        paymentId,
                        PaymentStatus.PROCESSING
                )
        );

        verify(paymentRepository, never())
                .updateStatusIfCurrent(
                        any(),
                        anyString(),
                        anyString()
                );

        verify(historyRepository, never())
                .save(any(PaymentStatusHistory.class));
    }

    // 8. Concurrent status update conflict
    @Test
    void shouldRejectUpdateWhenPaymentChangesConcurrently() {

        UUID paymentId = UUID.randomUUID();

        Payment pending = createPayment(PaymentStatus.PENDING);

        when(paymentRepository.findById(paymentId))
                .thenReturn(Optional.of(pending));

        when(paymentRepository.updateStatusIfCurrent(
                paymentId,
                "PENDING",
                "PROCESSING"
        )).thenReturn(0);

        assertThrows(
                InvalidPaymentStatusTransitionException.class,
                () -> paymentService.updatePaymentStatus(
                        paymentId,
                        PaymentStatus.PROCESSING
                )
        );

        verify(historyRepository, never())
                .save(any(PaymentStatusHistory.class));
    }
}
