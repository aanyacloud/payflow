
package com.payflow.backend;

import com.payflow.backend.dto.CreateRefundRequest;
import com.payflow.backend.dto.RefundResponse;
import com.payflow.backend.dto.RefundStatusHistoryResponse;
import com.payflow.backend.dto.UpdateRefundStatusRequest;

import com.payflow.backend.entity.Refund;
import com.payflow.backend.service.RefundService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments/{paymentId}/refunds")
public class RefundController {

    private final RefundService refundService;

    public RefundController(RefundService refundService) {
        this.refundService = refundService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RefundResponse createRefund(
            @PathVariable UUID paymentId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreateRefundRequest request
    ) {
        Refund refund = refundService.createRefund(
                paymentId,
                request.amount(),
                idempotencyKey
        );

        return toResponse(refund);
    }

    @GetMapping
    public List<RefundResponse> getRefunds(
            @PathVariable UUID paymentId
    ) {
        return refundService.getRefunds(paymentId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @PatchMapping("/{refundId}/status")
    public RefundResponse updateRefundStatus(
            @PathVariable UUID paymentId,
            @PathVariable UUID refundId,
            @Valid @RequestBody UpdateRefundStatusRequest request
    ) {
        Refund refund = refundService.updateRefundStatus(
                paymentId,
                refundId,
                request.status()
        );

        return toResponse(refund);
    }

    @GetMapping("/{refundId}/history")
    public List<RefundStatusHistoryResponse> getRefundStatusHistory(
            @PathVariable UUID paymentId,
            @PathVariable UUID refundId
    ) {
        return refundService.getRefundStatusHistory(
                paymentId,
                refundId
        );
    }

    private RefundResponse toResponse(Refund refund) {
        return new RefundResponse(
                refund.getId(),
                refund.getPayment().getId(),
                refund.getAmount(),
                refund.getStatus(),
                refund.getIdempotencyKey(),
                refund.getCreatedAt()
        );
    }
}
