
package com.payflow.backend.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
    name = "refund_status_history",
    indexes = {
        @Index(
            name = "idx_refund_history_refund_id",
            columnList = "refund_id"
        )
    }
)
public class RefundStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "refund_id", nullable = false)
    private Refund refund;

    @Column(name = "previous_status", nullable = false, length = 20)
    private String previousStatus;

    @Column(name = "new_status", nullable = false, length = 20)
    private String newStatus;

    @Column(name = "changed_at", nullable = false, updatable = false)
    private Instant changedAt;

    protected RefundStatusHistory() {
    }

    public RefundStatusHistory(
            Refund refund,
            String previousStatus,
            String newStatus
    ) {
        this.refund = refund;
        this.previousStatus = previousStatus;
        this.newStatus = newStatus;
        this.changedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public Refund getRefund() {
        return refund;
    }

    public String getPreviousStatus() {
        return previousStatus;
    }

    public String getNewStatus() {
        return newStatus;
    }

    public Instant getChangedAt() {
        return changedAt;
    }
}
