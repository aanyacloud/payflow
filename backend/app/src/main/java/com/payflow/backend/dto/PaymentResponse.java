
package com.payflow.backend.dto;

import com.payflow.backend.entity.Payment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentResponse(
        UUID id,
        BigDecimal amount,
        String currency,
        String status,
        Instant createdAt,
        String idempotencyKey
) {
    public static PaymentResponse from(Payment payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getStatus(),
                payment.getCreatedAt(),
                payment.getIdempotencyKey()
        );
    }
}
