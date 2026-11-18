
package com.payflow.backend.exception;

public class RefundNotFoundException extends RuntimeException {

    public RefundNotFoundException(String message) {
        super(message);
    }
}
