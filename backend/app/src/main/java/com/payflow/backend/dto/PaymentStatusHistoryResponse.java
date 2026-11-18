
package com.payflow.backend.dto;

import java.time.Instant;
import java.util.UUID;

public record PaymentStatusHistoryResponse(
        UUID id,
        UUID paymentId,
        String previousStatus,
        String newStatus,
        Instant changedAt
) {}
