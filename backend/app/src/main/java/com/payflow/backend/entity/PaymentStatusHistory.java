
package com.payflow.backend.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "payment_status_history",
        indexes = {
                @Index(
                        name = "idx_payment_history_payment_id",
                        columnList = "payment_id"
                )
        }
)
public class PaymentStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

    @Column(name = "previous_status", nullable = false, length = 20)
    private String previousStatus;

    @Column(name = "new_status", nullable = false, length = 20)
    private String newStatus;

    @Column(name = "changed_at", nullable = false, updatable = false)
    private Instant changedAt;

    protected PaymentStatusHistory() {
    }

    public PaymentStatusHistory(
            Payment payment,
            String previousStatus,
            String newStatus
    ) {
        this.payment = payment;
        this.previousStatus = previousStatus;
        this.newStatus = newStatus;
        this.changedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public Payment getPayment() {
        return payment;
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
