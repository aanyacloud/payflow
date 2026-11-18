
package com.payflow.backend.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
    name = "refunds",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_refund_idempotency_key",
            columnNames = "idempotency_key"
        )
    },
    indexes = {
        @Index(
            name = "idx_refunds_payment_id",
            columnList = "payment_id"
        )
    }
)
public class Refund {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(
        name = "idempotency_key",
        nullable = false,
        updatable = false,
        length = 100
    )
    private String idempotencyKey;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected Refund() {
    }

    public Refund(
            Payment payment,
            BigDecimal amount,
            String status,
            String idempotencyKey
    ) {
        this.payment = payment;
        this.amount = amount;
        this.status = status;
        this.idempotencyKey = idempotencyKey;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public Payment getPayment() {
        return payment;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getStatus() {
        return status;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    // Update refund status.
    public void setStatus(String status) {
        this.status = status;
    }
}
