
package com.payflow.backend.dto;

import com.payflow.backend.entity.RefundStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateRefundStatusRequest(
        @NotNull
        RefundStatus status
) {}
