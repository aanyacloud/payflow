
package com.payflow.backend.repository;

import com.payflow.backend.entity.Payment;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    long countByIdempotencyKey(String idempotencyKey);

    // Lock payment row while processing a refund.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.id = :paymentId")
    Optional<Payment> findByIdForUpdate(
            @Param("paymentId") UUID paymentId
    );

    @Modifying
    @Query(value = """
            INSERT INTO payments
                (id, amount, currency, status, created_at, idempotency_key)
            VALUES
                (:id, :amount, :currency, :status, :createdAt, :idempotencyKey)
            ON CONFLICT (idempotency_key) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("id") UUID id,
            @Param("amount") BigDecimal amount,
            @Param("currency") String currency,
            @Param("status") String status,
            @Param("createdAt") Instant createdAt,
            @Param("idempotencyKey") String idempotencyKey
    );

    // Update only when the current status matches the expected status.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE Payment p
            SET p.status = :newStatus
            WHERE p.id = :paymentId
            AND p.status = :expectedStatus
            """)
    int updateStatusIfCurrent(
            @Param("paymentId") UUID paymentId,
            @Param("expectedStatus") String expectedStatus,
            @Param("newStatus") String newStatus
    );
}
