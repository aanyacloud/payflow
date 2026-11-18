
package com.payflow.backend.dto;

import java.time.Instant;
import java.util.UUID;

public record RefundStatusHistoryResponse(
        UUID id,
        UUID refundId,
        String previousStatus,
        String newStatus,
        Instant changedAt
) {}
