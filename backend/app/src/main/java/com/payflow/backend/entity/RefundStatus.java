
package com.payflow.backend.entity;

public enum RefundStatus {
    PENDING,
    PROCESSING,
    SUCCESS,
    FAILED;

    public boolean canTransitionTo(RefundStatus nextStatus) {
        return switch (this) {
            case PENDING ->
                    nextStatus == PROCESSING ||
                    nextStatus == FAILED;

            case PROCESSING ->
                    nextStatus == SUCCESS ||
                    nextStatus == FAILED;

            case SUCCESS, FAILED -> false;
        };
    }
}
