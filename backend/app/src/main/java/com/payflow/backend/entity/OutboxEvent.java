
package com.payflow.backend.entity;

import java.time.Instant;
import java.util.UUID;

public record OutboxEvent(
        UUID eventId,
        String aggregateType,
        UUID aggregateId,
        String eventType,
        String payload,
        Instant createdAt,
        Instant publishedAt,
        int attempts,
        String lastError
) {
}
