
package com.payflow.backend.repository;

import com.payflow.backend.entity.RefundStatusHistory;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RefundStatusHistoryRepository
        extends JpaRepository<RefundStatusHistory, UUID> {

    List<RefundStatusHistory>
            findAllByRefund_IdOrderByChangedAtAsc(UUID refundId);
}
