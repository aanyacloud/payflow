
package com.payflow.backend.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record RefundResponse(
        UUID id,
        UUID paymentId,
        BigDecimal amount,
        String status,
        String idempotencyKey,
        Instant createdAt
) {}
