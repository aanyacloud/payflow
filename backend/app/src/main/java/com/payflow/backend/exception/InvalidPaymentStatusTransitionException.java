package com.payflow.backend.exception;

public class InvalidPaymentStatusTransitionException
        extends RuntimeException {

    public InvalidPaymentStatusTransitionException(String message) {
        super(message);
    }
}
