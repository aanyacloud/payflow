
package com.payflow.backend.service;

import com.payflow.backend.dto.RefundStatusHistoryResponse;
import com.payflow.backend.entity.Payment;
import com.payflow.backend.entity.Refund;
import com.payflow.backend.entity.RefundStatus;
import com.payflow.backend.entity.RefundStatusHistory;
import com.payflow.backend.exception.IdempotencyConflictException;
import com.payflow.backend.exception.InvalidPaymentStatusTransitionException;
import com.payflow.backend.exception.PaymentNotFoundException;
import com.payflow.backend.exception.RefundNotFoundException;
import com.payflow.backend.repository.PaymentRepository;
import com.payflow.backend.repository.RefundRepository;
import com.payflow.backend.repository.RefundStatusHistoryRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RefundServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private RefundRepository refundRepository;

    @Mock
    private RefundStatusHistoryRepository historyRepository;

    @InjectMocks
    private RefundService refundService;

    private UUID userId;
    private UUID paymentId;
    private UUID refundId;

    private Payment payment;
    private Refund refund;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        paymentId = UUID.randomUUID();
        refundId = UUID.randomUUID();

        payment = mock(Payment.class);
        refund = mock(Refund.class);
    }

    private void mockOwnedPayment() {
        when(paymentRepository.findByIdAndUserIdForUpdate(paymentId, userId))
                .thenReturn(Optional.of(payment));
    }

    private void mockPaymentForStatusUpdate() {
        when(paymentRepository.findByIdAndUserIdForUpdate(paymentId, userId))
                .thenReturn(Optional.of(payment));
    }

    @Test
    void createRefundSuccessfully() {
        mockOwnedPayment();

        when(refundRepository.findByPayment_IdAndIdempotencyKey(
                paymentId, "refund-001"
        )).thenReturn(Optional.empty());

        when(payment.getAmount()).thenReturn(new BigDecimal("500.00"));
        when(payment.getStatus()).thenReturn("SUCCESS");

        when(refundRepository.calculateReservedRefundAmount(paymentId))
                .thenReturn(new BigDecimal("100.00"));

        when(refundRepository.save(any(Refund.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Refund result = refundService.createRefund(
                userId, paymentId, new BigDecimal("200.00"), "refund-001"
        );

        assertNotNull(result);
        assertEquals("PENDING", result.getStatus());
        assertEquals(new BigDecimal("200.00"), result.getAmount());

        verify(paymentRepository).findByIdAndUserIdForUpdate(paymentId, userId);
        verify(refundRepository).save(any(Refund.class));
    }

    @Test
    void createRefundRejectsZeroOrNegativeAmount() {
        assertThrows(IllegalArgumentException.class,
                () -> refundService.createRefund(
                        userId, paymentId, BigDecimal.ZERO, "refund-002"
                ));

        assertThrows(IllegalArgumentException.class,
                () -> refundService.createRefund(
                        userId, paymentId, new BigDecimal("-10.00"), "refund-003"
                ));

        verifyNoInteractions(paymentRepository, refundRepository);
    }

    @Test
    void createRefundRejectsInvalidIdempotencyKey() {
        assertThrows(IllegalArgumentException.class,
                () -> refundService.createRefund(
                        userId, paymentId, new BigDecimal("100.00"), " "
                ));

        assertThrows(IllegalArgumentException.class,
                () -> refundService.createRefund(
                        userId, paymentId, new BigDecimal("100.00"), null
                ));

        assertThrows(IllegalArgumentException.class,
                () -> refundService.createRefund(
                        userId, paymentId, new BigDecimal("100.00"), "a".repeat(101)
                ));

        verifyNoInteractions(paymentRepository, refundRepository);
    }

    @Test
    void createRefundReturnsExistingRefundForSameRequest() {
        mockOwnedPayment();

        when(refundRepository.findByPayment_IdAndIdempotencyKey(
                paymentId, "refund-004"
        )).thenReturn(Optional.of(refund));

        when(refund.getAmount()).thenReturn(new BigDecimal("100.00"));

        Refund result = refundService.createRefund(
                userId, paymentId, new BigDecimal("100.00"), "refund-004"
        );

        assertSame(refund, result);
        verify(refundRepository, never()).save(any());
        verify(payment, never()).getStatus();
    }

    @Test
    void createRefundRejectsReusedKeyWithDifferentAmount() {
        mockOwnedPayment();

        when(refundRepository.findByPayment_IdAndIdempotencyKey(
                paymentId, "refund-005"
        )).thenReturn(Optional.of(refund));

        when(refund.getAmount()).thenReturn(new BigDecimal("100.00"));

        assertThrows(IdempotencyConflictException.class,
                () -> refundService.createRefund(
                        userId, paymentId, new BigDecimal("150.00"), "refund-005"
                ));

        verify(refundRepository, never()).save(any());
    }

    @Test
    void createRefundRejectsMissingPayment() {
        when(paymentRepository.findByIdAndUserIdForUpdate(paymentId, userId))
                .thenReturn(Optional.empty());

        assertThrows(PaymentNotFoundException.class,
                () -> refundService.createRefund(
                        userId, paymentId, new BigDecimal("100.00"), "refund-006"
                ));

        verifyNoInteractions(refundRepository);
    }

    @Test
    void createRefundRejectsUnsuccessfulPayment() {
        mockOwnedPayment();

        when(refundRepository.findByPayment_IdAndIdempotencyKey(
                paymentId, "refund-007"
        )).thenReturn(Optional.empty());

        when(payment.getStatus()).thenReturn("FAILED");

        assertThrows(IllegalArgumentException.class,
                () -> refundService.createRefund(
                        userId, paymentId, new BigDecimal("100.00"), "refund-007"
                ));

        verify(refundRepository, never()).save(any());
    }

    @Test
    void createRefundRejectsAmountAboveRemainingBalance() {
        mockOwnedPayment();

        when(refundRepository.findByPayment_IdAndIdempotencyKey(
                paymentId, "refund-008"
        )).thenReturn(Optional.empty());

        when(payment.getStatus()).thenReturn("SUCCESS");
        when(payment.getAmount()).thenReturn(new BigDecimal("500.00"));

        when(refundRepository.calculateReservedRefundAmount(paymentId))
                .thenReturn(new BigDecimal("450.00"));

        assertThrows(IllegalArgumentException.class,
                () -> refundService.createRefund(
                        userId, paymentId, new BigDecimal("100.00"), "refund-008"
                ));

        verify(refundRepository, never()).save(any());
    }

    @Test
    void createRefundHandlesNullReservedAmount() {
        mockOwnedPayment();

        when(refundRepository.findByPayment_IdAndIdempotencyKey(
                paymentId, "refund-010"
        )).thenReturn(Optional.empty());

        when(payment.getStatus()).thenReturn("SUCCESS");
        when(payment.getAmount()).thenReturn(new BigDecimal("500.00"));

        when(refundRepository.calculateReservedRefundAmount(paymentId))
                .thenReturn(null);

        when(refundRepository.save(any(Refund.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Refund result = refundService.createRefund(
                userId, paymentId, new BigDecimal("100.00"), "refund-010"
        );

        assertNotNull(result);
        assertEquals("PENDING", result.getStatus());
        verify(refundRepository).save(any(Refund.class));
    }

    @Test
    void updateRefundStatusSuccessfullyRecordsHistory() {
        mockPaymentForStatusUpdate();

        when(refundRepository.findByIdAndPayment_IdForUpdate(refundId, paymentId))
                .thenReturn(Optional.of(refund));

        when(refund.getStatus()).thenReturn("PENDING");

        when(refundRepository.updateStatusIfCurrent(
                refundId, paymentId, "PENDING", "PROCESSING"
        )).thenReturn(1);

        when(refundRepository.save(refund)).thenReturn(refund);

        Refund result = refundService.updateRefundStatus(
                userId, paymentId, refundId, RefundStatus.PROCESSING
        );

        assertSame(refund, result);

        verify(paymentRepository).findByIdAndUserIdForUpdate(paymentId, userId);
        verify(refundRepository).updateStatusIfCurrent(
                refundId, paymentId, "PENDING", "PROCESSING"
        );

        ArgumentCaptor<RefundStatusHistory> captor =
                ArgumentCaptor.forClass(RefundStatusHistory.class);

        verify(historyRepository).save(captor.capture());

        assertEquals("PENDING", captor.getValue().getPreviousStatus());
        assertEquals("PROCESSING", captor.getValue().getNewStatus());
        verify(refundRepository).save(refund);
    }

    @Test
    void updateRefundStatusRejectsInvalidTransition() {
        mockPaymentForStatusUpdate();

        when(refundRepository.findByIdAndPayment_IdForUpdate(refundId, paymentId))
                .thenReturn(Optional.of(refund));

        when(refund.getStatus()).thenReturn("SUCCESS");

        assertThrows(InvalidPaymentStatusTransitionException.class,
                () -> refundService.updateRefundStatus(
                        userId, paymentId, refundId, RefundStatus.PROCESSING
                ));

        verify(refundRepository, never())
                .updateStatusIfCurrent(any(), any(), anyString(), anyString());

        verify(historyRepository, never()).save(any());
        verify(refundRepository, never()).save(any());
    }

    @Test
    void updateRefundStatusRejectsMissingRefund() {
        mockPaymentForStatusUpdate();

        when(refundRepository.findByIdAndPayment_IdForUpdate(refundId, paymentId))
                .thenReturn(Optional.empty());

        assertThrows(RefundNotFoundException.class,
                () -> refundService.updateRefundStatus(
                        userId, paymentId, refundId, RefundStatus.PROCESSING
                ));

        verify(refundRepository, never())
                .updateStatusIfCurrent(any(), any(), anyString(), anyString());
    }

    @Test
    void updateRefundStatusRejectsMissingPayment() {
        when(paymentRepository.findByIdAndUserIdForUpdate(paymentId, userId))
                .thenReturn(Optional.empty());

        assertThrows(PaymentNotFoundException.class,
                () -> refundService.updateRefundStatus(
                        userId, paymentId, refundId, RefundStatus.PROCESSING
                ));

        verify(refundRepository, never())
                .findByIdAndPayment_IdForUpdate(any(), any());
    }

    @Test
    void updateRefundStatusRejectsConcurrentChange() {
        mockPaymentForStatusUpdate();

        when(refundRepository.findByIdAndPayment_IdForUpdate(refundId, paymentId))
                .thenReturn(Optional.of(refund));

        when(refund.getStatus()).thenReturn("PENDING");

        when(refundRepository.updateStatusIfCurrent(
                refundId, paymentId, "PENDING", "PROCESSING"
        )).thenReturn(0);

        assertThrows(InvalidPaymentStatusTransitionException.class,
                () -> refundService.updateRefundStatus(
                        userId, paymentId, refundId, RefundStatus.PROCESSING
                ));

        verify(historyRepository, never()).save(any());
        verify(refundRepository, never()).save(any());
    }

    @Test
    void getRefundStatusHistoryReturnsRecords() {
        when(paymentRepository.findByIdAndUser_Id(paymentId, userId))
                .thenReturn(Optional.of(payment));

        when(refundRepository.findByIdAndPayment_Id(refundId, paymentId))
                .thenReturn(Optional.of(refund));

        when(historyRepository.findAllByRefund_IdOrderByChangedAtAsc(refundId))
                .thenReturn(List.of());

        List<RefundStatusHistoryResponse> result =
                refundService.getRefundStatusHistory(userId, paymentId, refundId);

        assertNotNull(result);
        assertTrue(result.isEmpty());

        verify(historyRepository)
                .findAllByRefund_IdOrderByChangedAtAsc(refundId);
    }

    @Test
    void getRefundStatusHistoryRejectsMissingPayment() {
        when(paymentRepository.findByIdAndUser_Id(paymentId, userId))
                .thenReturn(Optional.empty());

        assertThrows(PaymentNotFoundException.class,
                () -> refundService.getRefundStatusHistory(
                        userId, paymentId, refundId
                ));

        verify(refundRepository, never())
                .findByIdAndPayment_Id(any(), any());
        verifyNoInteractions(historyRepository);
    }

    @Test
    void getRefundStatusHistoryRejectsMissingRefund() {
        when(paymentRepository.findByIdAndUser_Id(paymentId, userId))
                .thenReturn(Optional.of(payment));

        when(refundRepository.findByIdAndPayment_Id(refundId, paymentId))
                .thenReturn(Optional.empty());

        assertThrows(RefundNotFoundException.class,
                () -> refundService.getRefundStatusHistory(
                        userId, paymentId, refundId
                ));

        verify(historyRepository, never())
                .findAllByRefund_IdOrderByChangedAtAsc(any());
    }
}
