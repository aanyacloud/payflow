
package com.payflow.backend.repository;

import com.payflow.backend.entity.Payment;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    Optional<Payment> findByUser_IdAndIdempotencyKey(
            UUID userId,
            String idempotencyKey
    );

    Optional<Payment> findByIdAndUser_Id(
            UUID paymentId,
            UUID userId
    );

    Page<Payment> findAllByUser_Id(
            UUID userId,
            Pageable pageable
    );

    long countByUser_IdAndIdempotencyKey(
            UUID userId,
            String idempotencyKey
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT p FROM Payment p
            WHERE p.id = :paymentId
            AND p.user.id = :userId
            """)
    Optional<Payment> findByIdAndUserIdForUpdate(
            @Param("paymentId") UUID paymentId,
            @Param("userId") UUID userId
    );

    @Modifying
    @Query(value = """
            INSERT INTO payments
                (id, user_id, amount, currency, status,
                 created_at, idempotency_key)
            VALUES
                (:id, :userId, :amount, :currency, :status,
                 :createdAt, :idempotencyKey)
            ON CONFLICT (user_id, idempotency_key) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("id") UUID id,
            @Param("userId") UUID userId,
            @Param("amount") BigDecimal amount,
            @Param("currency") String currency,
            @Param("status") String status,
            @Param("createdAt") Instant createdAt,
            @Param("idempotencyKey") String idempotencyKey
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE Payment p
            SET p.status = :newStatus
            WHERE p.id = :paymentId
            AND p.user.id = :userId
            AND p.status = :expectedStatus
            """)
    int updateStatusIfCurrent(
            @Param("paymentId") UUID paymentId,
            @Param("userId") UUID userId,
            @Param("expectedStatus") String expectedStatus,
            @Param("newStatus") String newStatus
    );
}
