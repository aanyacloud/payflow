
package com.payflow.backend.entity;

public enum PaymentStatus {

    PENDING,
    PROCESSING,
    SUCCESS,
    FAILED;

    public boolean canTransitionTo(PaymentStatus nextStatus) {

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
