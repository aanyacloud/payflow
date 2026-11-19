
package com.payflow.backend.service;

import com.payflow.backend.entity.Payment;
import com.payflow.backend.entity.Refund;
import com.payflow.backend.entity.RefundStatus;
import com.payflow.backend.entity.RefundStatusHistory;

import com.payflow.backend.dto.RefundStatusHistoryResponse;

import com.payflow.backend.exception.PaymentNotFoundException;
import com.payflow.backend.exception.RefundNotFoundException;
import com.payflow.backend.exception.IdempotencyConflictException;
import com.payflow.backend.exception.InvalidPaymentStatusTransitionException;

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

        var existingRefund =
                refundRepository.findByIdempotencyKey(idempotencyKey);

        if (existingRefund.isPresent()) {

            Refund refund = existingRefund.get();

            boolean samePayment =
                    refund.getPayment().getId().equals(paymentId);

            boolean sameAmount =
                    refund.getAmount().compareTo(amount) == 0;

            if (!samePayment || !sameAmount) {
                throw new IdempotencyConflictException(
                        "Idempotency key already used with a different refund request"
                );
            }

            return refund;
        }

        // Lock the parent payment to serialize refund operations.
        Payment payment = paymentRepository
                .findByIdForUpdate(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException(
                        "Payment not found: " + paymentId
                ));

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
    public List<Refund> getRefunds(UUID paymentId) {

        paymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException(
                        "Payment not found: " + paymentId
                ));

        return refundRepository
                .findAllByPayment_IdOrderByCreatedAtAsc(paymentId);
    }

    @Transactional
    public Refund updateRefundStatus(
            UUID paymentId,
            UUID refundId,
            RefundStatus requestedStatus
    ) {

        // Step 1: Lock the parent payment.
        paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException(
                        "Payment not found: " + paymentId
                ));

        // Step 2: Lock and retrieve the refund.
        Refund refund = refundRepository
                .findByIdAndPayment_IdForUpdate(refundId, paymentId)
                .orElseThrow(() -> new RefundNotFoundException(
                        "Refund not found: " + refundId
                ));

        // Step 3: Read and validate the current status.
        RefundStatus currentStatus =
                RefundStatus.valueOf(refund.getStatus());

        if (!currentStatus.canTransitionTo(requestedStatus)) {
            throw new InvalidPaymentStatusTransitionException(
                    "Invalid refund status transition: "
                            + currentStatus + " -> " + requestedStatus
            );
        }

        // Step 4: Perform an atomic conditional database update.
        int updated = refundRepository.updateStatusIfCurrent(
                refundId,
                paymentId,
                currentStatus.name(),
                requestedStatus.name()
        );

        // Zero updated rows means the expected state no longer matches.
        if (updated == 0) {
            throw new InvalidPaymentStatusTransitionException(
                    "Refund status changed concurrently. Please retry."
            );
        }

        // Step 5: Synchronize the managed entity.
        refund.setStatus(requestedStatus.name());

        // Step 6: Record history in the same transaction.
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
            UUID paymentId,
            UUID refundId
    ) {

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
