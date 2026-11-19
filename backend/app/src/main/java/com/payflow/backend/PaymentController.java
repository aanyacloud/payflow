
package com.payflow.backend;

import com.payflow.backend.dto.CreatePaymentRequest;
import com.payflow.backend.dto.PaymentResponse;
import com.payflow.backend.dto.UpdatePaymentStatusRequest;
import com.payflow.backend.dto.PaymentStatusHistoryResponse;
import com.payflow.backend.service.PaymentService;
import com.payflow.backend.validation.IdempotencyKeyValidator;

import jakarta.validation.Valid;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentResponse createPayment(
            @Valid @RequestBody CreatePaymentRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @AuthenticationPrincipal Jwt jwt
    ) {
        IdempotencyKeyValidator.validate(idempotencyKey);

        return PaymentResponse.from(
                paymentService.createPayment(
                        UUID.fromString(jwt.getSubject()),
                        request.amount(),
                        request.currency(),
                        idempotencyKey
                )
        );
    }

    @GetMapping("/{id}")
    @Cacheable(
            cacheNames = "payments",
            key = "#jwt.subject + ':' + #id"
    )
    public PaymentResponse getPayment(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return PaymentResponse.from(
                paymentService.getPaymentById(
                        UUID.fromString(jwt.getSubject()),
                        id
                )
        );
    }

    @GetMapping
    public Page<PaymentResponse> getPayments(
            @PageableDefault(
                    size = 10,
                    sort = "createdAt",
                    direction = Sort.Direction.DESC
            ) Pageable pageable,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return paymentService.getPayments(
                UUID.fromString(jwt.getSubject()),
                pageable
        ).map(PaymentResponse::from);
    }

    @PatchMapping("/{id}/status")
    @CacheEvict(
            cacheNames = "payments",
            key = "#jwt.subject + ':' + #id"
    )
    public PaymentResponse updatePaymentStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdatePaymentStatusRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return PaymentResponse.from(
                paymentService.updatePaymentStatus(
                        UUID.fromString(jwt.getSubject()),
                        id,
                        request.status()
                )
        );
    }

    @GetMapping("/{id}/history")
    public List<PaymentStatusHistoryResponse> getPaymentStatusHistory(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return paymentService.getPaymentStatusHistory(
                UUID.fromString(jwt.getSubject()),
                id
        );
    }
}
