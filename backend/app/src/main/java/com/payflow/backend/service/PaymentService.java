
package com.payflow.backend.service;

import com.payflow.backend.dto.PaymentStatusHistoryResponse;
import com.payflow.backend.entity.OutboxEvent;
import com.payflow.backend.entity.Payment;
import com.payflow.backend.entity.PaymentStatus;
import com.payflow.backend.entity.PaymentStatusHistory;
import com.payflow.backend.entity.User;
import com.payflow.backend.events.PaymentCreatedEvent;
import com.payflow.backend.exception.PaymentNotFoundException;
import com.payflow.backend.exception.IdempotencyConflictException;
import com.payflow.backend.exception.InvalidPaymentStatusTransitionException;
import com.payflow.backend.repository.OutboxEventRepository;
import com.payflow.backend.repository.PaymentRepository;
import com.payflow.backend.repository.PaymentStatusHistoryRepository;
import com.payflow.backend.repository.UserRepository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class PaymentService {

    private static final String AGGREGATE_TYPE = "PAYMENT";
    private static final String PAYMENT_CREATED = "PAYMENT_CREATED";

    private final PaymentRepository paymentRepository;
    private final PaymentStatusHistoryRepository historyRepository;
    private final UserRepository userRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public PaymentService(
            PaymentRepository paymentRepository,
            PaymentStatusHistoryRepository historyRepository,
            UserRepository userRepository,
            OutboxEventRepository outboxEventRepository,
            ObjectMapper objectMapper
    ) {
        this.paymentRepository = paymentRepository;
        this.historyRepository = historyRepository;
        this.userRepository = userRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Payment createPayment(
            UUID userId,
            BigDecimal amount,
            String currency,
            String idempotencyKey
    ) {
        validateIdempotencyKey(idempotencyKey);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException(
                        "Authenticated user not found"
                ));

        UUID paymentId = UUID.randomUUID();
        Instant createdAt = Instant.now();

        int inserted = paymentRepository.insertIfAbsent(
                paymentId,
                userId,
                amount,
                currency,
                PaymentStatus.PENDING.name(),
                createdAt,
                idempotencyKey
        );

        Payment existingPayment = paymentRepository
                .findByUser_IdAndIdempotencyKey(userId, idempotencyKey)
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

        // Only newly created payments generate an outbox event.
        if (inserted == 1) {

            PaymentCreatedEvent event = new PaymentCreatedEvent(
                    UUID.randomUUID(),
                    existingPayment.getId(),
                    userId,
                    existingPayment.getAmount(),
                    existingPayment.getCurrency(),
                    existingPayment.getStatus(),
                    createdAt
            );

            savePaymentCreatedEvent(event);
        }

        return existingPayment;
    }

    private void savePaymentCreatedEvent(PaymentCreatedEvent event) {

        try {
            String payload = objectMapper.writeValueAsString(event);

            OutboxEvent outboxEvent = new OutboxEvent(
                    event.eventId(),
                    AGGREGATE_TYPE,
                    event.paymentId(),
                    PAYMENT_CREATED,
                    payload,
                    event.occurredAt(),
                    null,
                    0,
                    null
            );

            outboxEventRepository.save(outboxEvent);

        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Failed to serialize payment-created event",
                    exception
            );
        }
    }

    private void validateIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null
                || idempotencyKey.isBlank()
                || idempotencyKey.length() > 100) {
            throw new IllegalArgumentException(
                    "Idempotency key must contain 1 to 100 characters"
            );
        }
    }

    @Transactional
    public Payment updatePaymentStatus(
            UUID userId,
            UUID paymentId,
            PaymentStatus requestedStatus
    ) {
        Payment payment = findOwnedPayment(userId, paymentId);

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
                userId,
                currentStatus.name(),
                requestedStatus.name()
        );

        if (updated == 0) {
            throw new InvalidPaymentStatusTransitionException(
                    "Payment status changed concurrently. Please retry."
            );
        }

        historyRepository.save(
                new PaymentStatusHistory(
                        payment,
                        currentStatus.name(),
                        requestedStatus.name()
                )
        );

        return findOwnedPayment(userId, paymentId);
    }

    @Transactional(readOnly = true)
    public List<PaymentStatusHistoryResponse> getPaymentStatusHistory(
            UUID userId,
            UUID paymentId
    ) {
        findOwnedPayment(userId, paymentId);

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
    public Payment getPaymentById(UUID userId, UUID paymentId) {
        return findOwnedPayment(userId, paymentId);
    }

    @Transactional(readOnly = true)
    public Page<Payment> getPayments(
            UUID userId,
            Pageable pageable
    ) {
        return paymentRepository.findAllByUser_Id(userId, pageable);
    }

    private Payment findOwnedPayment(UUID userId, UUID paymentId) {
        return paymentRepository.findByIdAndUser_Id(paymentId, userId)
                .orElseThrow(() -> new PaymentNotFoundException(
                        "Payment not found: " + paymentId
                ));
    }
}
