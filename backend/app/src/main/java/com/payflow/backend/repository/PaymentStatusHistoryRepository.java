
package com.payflow.backend.repository;

import com.payflow.backend.entity.PaymentStatusHistory;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PaymentStatusHistoryRepository
        extends JpaRepository<PaymentStatusHistory, UUID> {

    List<PaymentStatusHistory> findAllByPayment_IdOrderByChangedAtAsc(
            UUID paymentId
    );
}
