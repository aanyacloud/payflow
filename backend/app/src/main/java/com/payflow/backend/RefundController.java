
package com.payflow.backend;

import com.payflow.backend.dto.CreateRefundRequest;
import com.payflow.backend.dto.RefundResponse;
import com.payflow.backend.dto.RefundStatusHistoryResponse;
import com.payflow.backend.dto.UpdateRefundStatusRequest;
import com.payflow.backend.entity.Refund;
import com.payflow.backend.service.RefundService;
import com.payflow.backend.validation.IdempotencyKeyValidator;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
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
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID paymentId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreateRefundRequest request
    ) {
        IdempotencyKeyValidator.validate(idempotencyKey);

        UUID userId = UUID.fromString(jwt.getSubject());

        Refund refund = refundService.createRefund(
                userId,
                paymentId,
                request.amount(),
                idempotencyKey
        );

        return toResponse(refund);
    }

    @GetMapping
    public List<RefundResponse> getRefunds(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID paymentId
    ) {
        UUID userId = UUID.fromString(jwt.getSubject());

        return refundService.getRefunds(userId, paymentId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @PatchMapping("/{refundId}/status")
    public RefundResponse updateRefundStatus(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID paymentId,
            @PathVariable UUID refundId,
            @Valid @RequestBody UpdateRefundStatusRequest request
    ) {
        UUID userId = UUID.fromString(jwt.getSubject());

        Refund refund = refundService.updateRefundStatus(
                userId,
                paymentId,
                refundId,
                request.status()
        );

        return toResponse(refund);
    }

    @GetMapping("/{refundId}/history")
    public List<RefundStatusHistoryResponse> getRefundStatusHistory(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID paymentId,
            @PathVariable UUID refundId
    ) {
        UUID userId = UUID.fromString(jwt.getSubject());

        return refundService.getRefundStatusHistory(
                userId,
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
