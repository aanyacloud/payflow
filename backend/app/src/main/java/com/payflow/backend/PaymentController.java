
package com.payflow.backend;

import com.payflow.backend.dto.CreatePaymentRequest;
import com.payflow.backend.dto.UpdatePaymentStatusRequest;
import com.payflow.backend.dto.PaymentStatusHistoryResponse;

import com.payflow.backend.entity.Payment;

import com.payflow.backend.service.PaymentService;
import com.payflow.backend.validation.IdempotencyKeyValidator;

import jakarta.validation.Valid;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
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

    // Create a new payment.
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Payment createPayment(
            @Valid @RequestBody CreatePaymentRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey
    ) {

        // Validate the idempotency key at the API boundary.
        IdempotencyKeyValidator.validate(idempotencyKey);

        return paymentService.createPayment(
                request.amount(),
                request.currency(),
                idempotencyKey
        );
    }

    // Retrieve a payment by ID.
    @GetMapping("/{id}")
    public Payment getPayment(
            @PathVariable UUID id
    ) {
        return paymentService.getPaymentById(id);
    }

    // Retrieve payments with pagination.
    @GetMapping
    public Page<Payment> getPayments(
            @PageableDefault(
                    size = 10,
                    sort = "createdAt",
                    direction = Sort.Direction.DESC
            ) Pageable pageable
    ) {
        return paymentService.getPayments(pageable);
    }

    // Update payment status.
    @PatchMapping("/{id}/status")
    public Payment updatePaymentStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdatePaymentStatusRequest request
    ) {

        return paymentService.updatePaymentStatus(
                id,
                request.status()
        );
    }

    // Retrieve the complete status history of a payment.
    @GetMapping("/{id}/history")
    public List<PaymentStatusHistoryResponse> getPaymentStatusHistory(
            @PathVariable UUID id
    ) {

        return paymentService.getPaymentStatusHistory(id);
    }
}
