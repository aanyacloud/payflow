
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

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class RefundService {

    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;
    private final RefundStatusHistoryRepository historyRepository;

    public RefundService(
            PaymentRepository paymentRepository,
            RefundRepository refundRepository,
            RefundStatusHistoryRepository historyRepository
    ) {
        this.paymentRepository = paymentRepository;
        this.refundRepository = refundRepository;
        this.historyRepository = historyRepository;
    }

    @Transactional
    public Refund createRefund(
            UUID userId,
            UUID paymentId,
            BigDecimal amount,
            String idempotencyKey
    ) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(
                    "Refund amount must be greater than zero"
            );
        }

        if (idempotencyKey == null
                || idempotencyKey.isBlank()
                || idempotencyKey.length() > 100) {
            throw new IllegalArgumentException(
                    "Idempotency key must contain 1 to 100 characters"
            );
        }

        // Lock only a payment owned by the authenticated user.
        Payment payment = paymentRepository
                .findByIdAndUserIdForUpdate(paymentId, userId)
                .orElseThrow(() -> new PaymentNotFoundException(
                        "Payment not found: " + paymentId
                ));

        // Idempotency is scoped to this payment.
        var existingRefund = refundRepository
                .findByPayment_IdAndIdempotencyKey(paymentId, idempotencyKey);

        if (existingRefund.isPresent()) {
            Refund refund = existingRefund.get();

            if (refund.getAmount().compareTo(amount) != 0) {
                throw new IdempotencyConflictException(
                        "Idempotency key already used with a different refund request"
                );
            }

            return refund;
        }

        if (!"SUCCESS".equals(payment.getStatus())) {
            throw new IllegalArgumentException(
                    "Only successful payments can be refunded"
            );
        }

        BigDecimal alreadyRefunded =
                refundRepository.calculateReservedRefundAmount(paymentId);

        if (alreadyRefunded == null) {
            alreadyRefunded = BigDecimal.ZERO;
        }

        BigDecimal remainingAmount =
                payment.getAmount().subtract(alreadyRefunded);

        if (amount.compareTo(remainingAmount) > 0) {
            throw new IllegalArgumentException(
                    "Refund amount exceeds remaining refundable balance: "
                            + remainingAmount
            );
        }

        Refund refund = new Refund(
                payment,
                amount,
                RefundStatus.PENDING.name(),
                idempotencyKey
        );

        return refundRepository.save(refund);
    }

    @Transactional(readOnly = true)
    public List<Refund> getRefunds(UUID userId, UUID paymentId) {

        paymentRepository.findByIdAndUser_Id(paymentId, userId)
                .orElseThrow(() -> new PaymentNotFoundException(
                        "Payment not found: " + paymentId
                ));

        return refundRepository
                .findAllByPayment_IdOrderByCreatedAtAsc(paymentId);
    }

    @Transactional
    public Refund updateRefundStatus(
            UUID userId,
            UUID paymentId,
            UUID refundId,
            RefundStatus requestedStatus
    ) {
        // Lock the owned parent payment first.
        paymentRepository.findByIdAndUserIdForUpdate(paymentId, userId)
                .orElseThrow(() -> new PaymentNotFoundException(
                        "Payment not found: " + paymentId
                ));

        // Then lock the refund belonging to that payment.
        Refund refund = refundRepository
                .findByIdAndPayment_IdForUpdate(refundId, paymentId)
                .orElseThrow(() -> new RefundNotFoundException(
                        "Refund not found: " + refundId
                ));

        RefundStatus currentStatus =
                RefundStatus.valueOf(refund.getStatus());

        if (!currentStatus.canTransitionTo(requestedStatus)) {
            throw new InvalidPaymentStatusTransitionException(
                    "Invalid refund status transition: "
                            + currentStatus + " -> " + requestedStatus
            );
        }

        int updated = refundRepository.updateStatusIfCurrent(
                refundId,
                paymentId,
                currentStatus.name(),
                requestedStatus.name()
        );

        if (updated == 0) {
            throw new InvalidPaymentStatusTransitionException(
                    "Refund status changed concurrently. Please retry."
            );
        }

        refund.setStatus(requestedStatus.name());

        historyRepository.save(
                new RefundStatusHistory(
                        refund,
                        currentStatus.name(),
                        requestedStatus.name()
                )
        );

        return refundRepository.save(refund);
    }

    @Transactional(readOnly = true)
    public List<RefundStatusHistoryResponse> getRefundStatusHistory(
            UUID userId,
            UUID paymentId,
            UUID refundId
    ) {
        paymentRepository.findByIdAndUser_Id(paymentId, userId)
                .orElseThrow(() -> new PaymentNotFoundException(
                        "Payment not found: " + paymentId
                ));

        refundRepository.findByIdAndPayment_Id(refundId, paymentId)
                .orElseThrow(() -> new RefundNotFoundException(
                        "Refund not found: " + refundId
                ));

        return historyRepository
                .findAllByRefund_IdOrderByChangedAtAsc(refundId)
                .stream()
                .map(history -> new RefundStatusHistoryResponse(
                        history.getId(),
                        refundId,
                        history.getPreviousStatus(),
                        history.getNewStatus(),
                        history.getChangedAt()
                ))
                .toList();
    }
}
