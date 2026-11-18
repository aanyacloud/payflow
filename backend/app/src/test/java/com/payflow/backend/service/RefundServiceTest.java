
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
import static org.mockito.ArgumentMatchers.any;
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

    private UUID paymentId;
    private UUID refundId;

    private Payment payment;
    private Refund refund;

    @BeforeEach
    void setUp() {
        paymentId = UUID.randomUUID();
        refundId = UUID.randomUUID();

        payment = mock(Payment.class);
        refund = mock(Refund.class);
    }

    // --------------------------------------------------
    // CREATE REFUND TESTS
    // --------------------------------------------------

    @Test
    void createRefundSuccessfully() {

        when(refundRepository.findByIdempotencyKey("refund-001"))
                .thenReturn(Optional.empty());

        when(paymentRepository.findByIdForUpdate(paymentId))
                .thenReturn(Optional.of(payment));

        when(payment.getAmount())
                .thenReturn(new BigDecimal("500.00"));

        when(payment.getStatus())
                .thenReturn("SUCCESS");

        when(refundRepository.calculateReservedRefundAmount(paymentId))
                .thenReturn(new BigDecimal("100.00"));

        when(refundRepository.save(any(Refund.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Refund result = refundService.createRefund(
                paymentId,
                new BigDecimal("200.00"),
                "refund-001"
        );

        assertNotNull(result);
        assertEquals("PENDING", result.getStatus());
        assertEquals(
                new BigDecimal("200.00"),
                result.getAmount()
        );

        verify(paymentRepository).findByIdForUpdate(paymentId);
        verify(refundRepository).save(any(Refund.class));
    }

    @Test
    void createRefundRejectsZeroOrNegativeAmount() {

        assertThrows(
                IllegalArgumentException.class,
                () -> refundService.createRefund(
                        paymentId,
                        BigDecimal.ZERO,
                        "refund-002"
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> refundService.createRefund(
                        paymentId,
                        new BigDecimal("-10.00"),
                        "refund-003"
                )
        );

        verifyNoInteractions(paymentRepository, refundRepository);
    }

    @Test
    void createRefundRejectsInvalidIdempotencyKey() {

        assertThrows(
                IllegalArgumentException.class,
                () -> refundService.createRefund(
                        paymentId,
                        new BigDecimal("100.00"),
                        " "
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> refundService.createRefund(
                        paymentId,
                        new BigDecimal("100.00"),
                        null
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> refundService.createRefund(
                        paymentId,
                        new BigDecimal("100.00"),
                        "a".repeat(101)
                )
        );

        verifyNoInteractions(paymentRepository, refundRepository);
    }

    @Test
    void createRefundReturnsExistingRefundForSameRequest() {

        when(refundRepository.findByIdempotencyKey("refund-004"))
                .thenReturn(Optional.of(refund));

        when(refund.getPayment()).thenReturn(payment);
        when(payment.getId()).thenReturn(paymentId);

        when(refund.getAmount())
                .thenReturn(new BigDecimal("100.00"));

        Refund result = refundService.createRefund(
                paymentId,
                new BigDecimal("100.00"),
                "refund-004"
        );

        assertSame(refund, result);

        verify(paymentRepository, never()).findByIdForUpdate(any());
        verify(refundRepository, never()).save(any());
    }

    @Test
    void createRefundRejectsReusedKeyWithDifferentAmount() {

        when(refundRepository.findByIdempotencyKey("refund-005"))
                .thenReturn(Optional.of(refund));

        when(refund.getPayment()).thenReturn(payment);
        when(payment.getId()).thenReturn(paymentId);

        when(refund.getAmount())
                .thenReturn(new BigDecimal("100.00"));

        assertThrows(
                IdempotencyConflictException.class,
                () -> refundService.createRefund(
                        paymentId,
                        new BigDecimal("150.00"),
                        "refund-005"
                )
        );

        verify(paymentRepository, never()).findByIdForUpdate(any());
        verify(refundRepository, never()).save(any());
    }

    @Test
    void createRefundRejectsDifferentPaymentWithSameIdempotencyKey() {

        UUID differentPaymentId = UUID.randomUUID();

        when(refundRepository.findByIdempotencyKey("refund-009"))
                .thenReturn(Optional.of(refund));

        when(refund.getPayment()).thenReturn(payment);
        when(payment.getId()).thenReturn(differentPaymentId);
        when(refund.getAmount()).thenReturn(new BigDecimal("100.00"));

        assertThrows(
                IdempotencyConflictException.class,
                () -> refundService.createRefund(
                        paymentId,
                        new BigDecimal("100.00"),
                        "refund-009"
                )
        );

        verify(paymentRepository, never()).findByIdForUpdate(any());
    }

    @Test
    void createRefundRejectsMissingPayment() {

        when(refundRepository.findByIdempotencyKey("refund-006"))
                .thenReturn(Optional.empty());

        when(paymentRepository.findByIdForUpdate(paymentId))
                .thenReturn(Optional.empty());

        assertThrows(
                PaymentNotFoundException.class,
                () -> refundService.createRefund(
                        paymentId,
                        new BigDecimal("100.00"),
                        "refund-006"
                )
        );

        verify(refundRepository, never()).save(any());
    }

    @Test
    void createRefundRejectsUnsuccessfulPayment() {

        when(refundRepository.findByIdempotencyKey("refund-007"))
                .thenReturn(Optional.empty());

        when(paymentRepository.findByIdForUpdate(paymentId))
                .thenReturn(Optional.of(payment));

        when(payment.getStatus()).thenReturn("FAILED");

        assertThrows(
                IllegalArgumentException.class,
                () -> refundService.createRefund(
                        paymentId,
                        new BigDecimal("100.00"),
                        "refund-007"
                )
        );

        verify(refundRepository, never()).save(any());
    }

    @Test
    void createRefundRejectsAmountAboveRemainingBalance() {

        when(refundRepository.findByIdempotencyKey("refund-008"))
                .thenReturn(Optional.empty());

        when(paymentRepository.findByIdForUpdate(paymentId))
                .thenReturn(Optional.of(payment));

        when(payment.getStatus()).thenReturn("SUCCESS");

        when(payment.getAmount())
                .thenReturn(new BigDecimal("500.00"));

        when(refundRepository.calculateReservedRefundAmount(paymentId))
                .thenReturn(new BigDecimal("450.00"));

        assertThrows(
                IllegalArgumentException.class,
                () -> refundService.createRefund(
                        paymentId,
                        new BigDecimal("100.00"),
                        "refund-008"
                )
        );

        verify(refundRepository, never()).save(any());
    }

    @Test
    void createRefundHandlesNullReservedAmount() {

        when(refundRepository.findByIdempotencyKey("refund-010"))
                .thenReturn(Optional.empty());

        when(paymentRepository.findByIdForUpdate(paymentId))
                .thenReturn(Optional.of(payment));

        when(payment.getStatus()).thenReturn("SUCCESS");

        when(payment.getAmount())
                .thenReturn(new BigDecimal("500.00"));

        when(refundRepository.calculateReservedRefundAmount(paymentId))
                .thenReturn(null);

        when(refundRepository.save(any(Refund.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Refund result = refundService.createRefund(
                paymentId,
                new BigDecimal("100.00"),
                "refund-010"
        );

        assertNotNull(result);
        assertEquals("PENDING", result.getStatus());

        verify(refundRepository).save(any(Refund.class));
    }

    // --------------------------------------------------
    // REFUND STATUS TESTS
    // --------------------------------------------------

    @Test
    void updateRefundStatusSuccessfullyRecordsHistory() {

        when(refundRepository.findByIdAndPayment_Id(refundId, paymentId))
                .thenReturn(Optional.of(refund));

        when(refund.getStatus()).thenReturn("PENDING");

        when(refundRepository.save(refund)).thenReturn(refund);

        Refund result = refundService.updateRefundStatus(
                paymentId,
                refundId,
                RefundStatus.PROCESSING
        );

        assertSame(refund, result);

        verify(refund).setStatus("PROCESSING");

        ArgumentCaptor<RefundStatusHistory> captor =
                ArgumentCaptor.forClass(RefundStatusHistory.class);

        verify(historyRepository).save(captor.capture());

        assertEquals(
                "PENDING",
                captor.getValue().getPreviousStatus()
        );

        assertEquals(
                "PROCESSING",
                captor.getValue().getNewStatus()
        );

        verify(refundRepository).save(refund);
    }

    @Test
    void updateRefundStatusRejectsInvalidTransition() {

        when(refundRepository.findByIdAndPayment_Id(refundId, paymentId))
                .thenReturn(Optional.of(refund));

        when(refund.getStatus()).thenReturn("SUCCESS");

        assertThrows(
                InvalidPaymentStatusTransitionException.class,
                () -> refundService.updateRefundStatus(
                        paymentId,
                        refundId,
                        RefundStatus.PROCESSING
                )
        );

        verify(historyRepository, never()).save(any());
        verify(refundRepository, never()).save(any());
        verify(refund, never()).setStatus(any());
    }

    @Test
    void updateRefundStatusRejectsMissingRefund() {

        when(refundRepository.findByIdAndPayment_Id(refundId, paymentId))
                .thenReturn(Optional.empty());

        assertThrows(
                RefundNotFoundException.class,
                () -> refundService.updateRefundStatus(
                        paymentId,
                        refundId,
                        RefundStatus.PROCESSING
                )
        );

        verify(historyRepository, never()).save(any());
    }

    // --------------------------------------------------
    // REFUND HISTORY TESTS
    // --------------------------------------------------

    @Test
    void getRefundStatusHistoryReturnsRecords() {

        when(refundRepository.findByIdAndPayment_Id(refundId, paymentId))
                .thenReturn(Optional.of(refund));

        when(historyRepository.findAllByRefund_IdOrderByChangedAtAsc(refundId))
                .thenReturn(List.of());

        List<RefundStatusHistoryResponse> result =
                refundService.getRefundStatusHistory(paymentId, refundId);

        assertNotNull(result);
        assertTrue(result.isEmpty());

        verify(historyRepository)
                .findAllByRefund_IdOrderByChangedAtAsc(refundId);
    }

    @Test
    void getRefundStatusHistoryRejectsMissingRefund() {

        when(refundRepository.findByIdAndPayment_Id(refundId, paymentId))
                .thenReturn(Optional.empty());

        assertThrows(
                RefundNotFoundException.class,
                () -> refundService.getRefundStatusHistory(
                        paymentId,
                        refundId
                )
        );

        verify(historyRepository, never())
                .findAllByRefund_IdOrderByChangedAtAsc(any());
    }
}
