
package com.payflow.backend.repository;

import com.payflow.backend.entity.Refund;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RefundRepository extends JpaRepository<Refund, UUID> {

    // Find refund using its idempotency key.
    Optional<Refund> findByIdempotencyKey(String idempotencyKey);

    // Find a refund belonging to a specific payment.
    Optional<Refund> findByIdAndPayment_Id(
            UUID refundId,
            UUID paymentId
    );

    // Retrieve all refunds for a payment.
    List<Refund> findAllByPayment_IdOrderByCreatedAtAsc(UUID paymentId);

    // Calculate the total amount reserved for refunds.
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
