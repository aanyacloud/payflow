
package com.payflow.backend.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payments")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "idempotency_key", unique = true, length = 100)
    private String idempotencyKey;

    protected Payment() {
    }

    public Payment(BigDecimal amount, String currency, String status) {
        this(amount, currency, status, null);
    }

    public Payment(
            BigDecimal amount,
            String currency,
            String status,
            String idempotencyKey
    ) {
        this.amount = amount;
        this.currency = currency;
        this.status = status;
        this.idempotencyKey = idempotencyKey;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public String getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }
}
