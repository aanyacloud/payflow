
package com.payflow.backend.repository;

import com.payflow.backend.entity.OutboxEvent;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public class OutboxEventRepository {

    private final JdbcTemplate jdbcTemplate;

    public OutboxEventRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<OutboxEvent> rowMapper = (rs, rowNum) ->
            new OutboxEvent(
                    rs.getObject("event_id", UUID.class),
                    rs.getString("aggregate_type"),
                    rs.getObject("aggregate_id", UUID.class),
                    rs.getString("event_type"),
                    rs.getString("payload"),
                    rs.getTimestamp("created_at").toInstant(),
                    rs.getTimestamp("published_at") != null
                            ? rs.getTimestamp("published_at").toInstant()
                            : null,
                    rs.getInt("attempts"),
                    rs.getString("last_error")
            );

    public void save(OutboxEvent event) {

        String sql = """
                INSERT INTO outbox_events (
                    event_id,
                    aggregate_type,
                    aggregate_id,
                    event_type,
                    payload,
                    created_at,
                    published_at,
                    attempts,
                    last_error
                )
                VALUES (?, ?, ?, ?, CAST(? AS JSONB), ?, ?, ?, ?)
                """;

        jdbcTemplate.update(
                sql,
                event.eventId(),
                event.aggregateType(),
                event.aggregateId(),
                event.eventType(),
                event.payload(),
                Timestamp.from(event.createdAt()),
                event.publishedAt() != null
                        ? Timestamp.from(event.publishedAt())
                        : null,
                event.attempts(),
                event.lastError()
        );
    }

    @Transactional
    public List<OutboxEvent> claimPendingEvents(int limit) {

        String sql = """
                WITH claimed AS (
                    SELECT event_id
                    FROM outbox_events
                    WHERE published_at IS NULL
                      AND (
                          claimed_at IS NULL
                          OR claimed_at < NOW() - INTERVAL '5 minutes'
                      )
                    ORDER BY created_at ASC
                    LIMIT ?
                    FOR UPDATE SKIP LOCKED
                )
                UPDATE outbox_events o
                SET claimed_at = NOW()
                FROM claimed c
                WHERE o.event_id = c.event_id
                RETURNING o.*
                """;

        return jdbcTemplate.query(sql, rowMapper, limit);
    }

    public void markPublished(UUID eventId, Instant publishedAt) {

        String sql = """
                UPDATE outbox_events
                SET published_at = ?,
                    claimed_at = NULL,
                    last_error = NULL
                WHERE event_id = ?
                  AND published_at IS NULL
                """;

        jdbcTemplate.update(
                sql,
                Timestamp.from(publishedAt),
                eventId
        );
    }

    public void recordFailure(UUID eventId, String errorMessage) {

        String sql = """
                UPDATE outbox_events
                SET attempts = attempts + 1,
                    claimed_at = NULL,
                    last_error = ?
                WHERE event_id = ?
                  AND published_at IS NULL
                """;

        jdbcTemplate.update(
                sql,
                errorMessage,
                eventId
        );
    }

    public long countPendingEvents() {

        String sql = """
                SELECT COUNT(*)
                FROM outbox_events
                WHERE published_at IS NULL
                """;

        Long count = jdbcTemplate.queryForObject(sql, Long.class);

        return count != null ? count : 0;
    }
}
