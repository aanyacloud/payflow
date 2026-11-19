
package com.payflow.backend.validation;

public final class IdempotencyKeyValidator {

    private static final int MAX_LENGTH = 100;

    private IdempotencyKeyValidator() {
        // Utility class
    }

    public static void validate(String idempotencyKey) {

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException(
                    "Idempotency key must not be blank"
            );
        }

        if (idempotencyKey.length() > MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "Idempotency key must not exceed 100 characters"
            );
        }
    }
}
