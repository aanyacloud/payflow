
package com.payflow.backend.dto;

import com.payflow.backend.entity.PaymentStatus;

import jakarta.validation.constraints.NotNull;

public record UpdatePaymentStatusRequest(

        @NotNull
        PaymentStatus status

) {}
