
package com.payflow.backend.repository;

import com.payflow.backend.entity.Refund;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RefundRepository extends JpaRepository<Refund, UUID> {

    Optional<Refund> findByPayment_IdAndIdempotencyKey(
        UUID paymentId,
        String idempotencyKey
);

    Optional<Refund> findByIdAndPayment_Id(
            UUID refundId,
            UUID paymentId
    );

    // Pessimistic lock for an individual refund.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT r FROM Refund r
            WHERE r.id = :refundId
            AND r.payment.id = :paymentId
            """)
    Optional<Refund> findByIdAndPayment_IdForUpdate(
            @Param("refundId") UUID refundId,
            @Param("paymentId") UUID paymentId
    );

    // Atomic compare-and-set status update.
    // Updates only if the refund still has the expected status.
    @Modifying(flushAutomatically = true)
    @Query("""
            UPDATE Refund r
            SET r.status = :newStatus
            WHERE r.id = :refundId
            AND r.payment.id = :paymentId
            AND r.status = :expectedStatus
            """)
    int updateStatusIfCurrent(
            @Param("refundId") UUID refundId,
            @Param("paymentId") UUID paymentId,
            @Param("expectedStatus") String expectedStatus,
            @Param("newStatus") String newStatus
    );

    List<Refund> findAllByPayment_IdOrderByCreatedAtAsc(UUID paymentId);

    @Query("""
            SELECT COALESCE(SUM(r.amount), 0)
            FROM Refund r
            WHERE r.payment.id = :paymentId
            AND r.status IN ('PENDING', 'PROCESSING', 'SUCCESS')
            """)
    BigDecimal calculateReservedRefundAmount(
            @Param("paymentId") UUID paymentId
    );
}
