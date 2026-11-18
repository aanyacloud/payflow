
package com.payflow.backend.service;

import com.payflow.backend.dto.PaymentStatusHistoryResponse;
import com.payflow.backend.entity.Payment;
import com.payflow.backend.entity.PaymentStatus;
import com.payflow.backend.entity.PaymentStatusHistory;
import com.payflow.backend.exception.PaymentNotFoundException;
import com.payflow.backend.exception.IdempotencyConflictException;
import com.payflow.backend.exception.InvalidPaymentStatusTransitionException;
import com.payflow.backend.repository.PaymentRepository;
import com.payflow.backend.repository.PaymentStatusHistoryRepository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentStatusHistoryRepository historyRepository;

    public PaymentService(
            PaymentRepository paymentRepository,
            PaymentStatusHistoryRepository historyRepository
    ) {
        this.paymentRepository = paymentRepository;
        this.historyRepository = historyRepository;
    }

    @Transactional
    public Payment createPayment(
            BigDecimal amount,
            String currency,
            String idempotencyKey
    ) {

        if (idempotencyKey == null
                || idempotencyKey.isBlank()
                || idempotencyKey.length() > 100) {

            throw new IllegalArgumentException(
                    "Idempotency key must contain 1 to 100 characters"
            );
        }

        UUID paymentId = UUID.randomUUID();
        Instant createdAt = Instant.now();

        int inserted = paymentRepository.insertIfAbsent(
                paymentId,
                amount,
                currency,
                PaymentStatus.PENDING.name(),
                createdAt,
                idempotencyKey
        );

        Payment existingPayment = paymentRepository
                .findByIdempotencyKey(idempotencyKey)
                .orElseThrow(() -> new IllegalStateException(
                        "Payment could not be retrieved after insertion"
                ));

        if (inserted == 0) {

            boolean sameAmount =
                    existingPayment.getAmount().compareTo(amount) == 0;

            boolean sameCurrency =
                    existingPayment.getCurrency().equals(currency);

            if (!sameAmount || !sameCurrency) {
                throw new IdempotencyConflictException(
                        "Idempotency key already used with a different request"
                );
            }
        }

        return existingPayment;
    }

    // Original overload retained for existing tests/internal callers.
    @Transactional
    public Payment createPayment(
            BigDecimal amount,
            String currency
    ) {

        Payment payment = new Payment(
                amount,
                currency,
                PaymentStatus.PENDING.name()
        );

        return paymentRepository.save(payment);
    }

    @Transactional
    public Payment updatePaymentStatus(
            UUID paymentId,
            PaymentStatus requestedStatus
    ) {

        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException(
                        "Payment not found: " + paymentId
                ));

        PaymentStatus currentStatus;

        try {
            currentStatus = PaymentStatus.valueOf(payment.getStatus());
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(
                    "Unknown payment status: " + payment.getStatus()
            );
        }

        if (!currentStatus.canTransitionTo(requestedStatus)) {
            throw new InvalidPaymentStatusTransitionException(
                    "Invalid payment status transition: "
                            + currentStatus + " -> " + requestedStatus
            );
        }

        int updated = paymentRepository.updateStatusIfCurrent(
                paymentId,
                currentStatus.name(),
                requestedStatus.name()
        );

        if (updated == 0) {
            throw new InvalidPaymentStatusTransitionException(
                    "Payment status changed concurrently. Please retry."
            );
        }

        // Record the successful transition in the same transaction.
        historyRepository.save(
                new PaymentStatusHistory(
                        payment,
                        currentStatus.name(),
                        requestedStatus.name()
                )
        );

        return paymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException(
                        "Payment not found: " + paymentId
                ));
    }

    @Transactional(readOnly = true)
    public List<PaymentStatusHistoryResponse> getPaymentStatusHistory(
            UUID paymentId
    ) {

        // Ensure the requested payment exists.
        paymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException(
                        "Payment not found: " + paymentId
                ));

        return historyRepository
                .findAllByPayment_IdOrderByChangedAtAsc(paymentId)
                .stream()
                .map(history -> new PaymentStatusHistoryResponse(
                        history.getId(),
                        paymentId,
                        history.getPreviousStatus(),
                        history.getNewStatus(),
                        history.getChangedAt()
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public Payment getPaymentById(UUID paymentId) {

        return paymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException(
                        "Payment not found: " + paymentId
                ));
    }

    @Transactional(readOnly = true)
    public Page<Payment> getPayments(Pageable pageable) {
        return paymentRepository.findAll(pageable);
    }
}
