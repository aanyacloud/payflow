
package com.payflow.backend.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record CreateRefundRequest(

        @NotNull(message = "Refund amount is required")
        @DecimalMin(
                value = "0.01",
                message = "Refund amount must be at least 0.01"
        )
        BigDecimal amount

) {}
