
package com.payflow.backend.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PaymentStatusTest {

    @Test
    void pendingCanTransitionToProcessing() {
        assertTrue(
                PaymentStatus.PENDING.canTransitionTo(
                        PaymentStatus.PROCESSING
                )
        );
    }

    @Test
    void pendingCanTransitionToFailed() {
        assertTrue(
                PaymentStatus.PENDING.canTransitionTo(
                        PaymentStatus.FAILED
                )
        );
    }

    @Test
    void processingCanTransitionToSuccess() {
        assertTrue(
                PaymentStatus.PROCESSING.canTransitionTo(
                        PaymentStatus.SUCCESS
                )
        );
    }

    @Test
    void processingCanTransitionToFailed() {
        assertTrue(
                PaymentStatus.PROCESSING.canTransitionTo(
                        PaymentStatus.FAILED
                )
        );
    }

    @Test
    void pendingCannotTransitionDirectlyToSuccess() {
        assertFalse(
                PaymentStatus.PENDING.canTransitionTo(
                        PaymentStatus.SUCCESS
                )
        );
    }

    @Test
    void successCannotTransitionToFailed() {
        assertFalse(
                PaymentStatus.SUCCESS.canTransitionTo(
                        PaymentStatus.FAILED
                )
        );
    }

    @Test
    void failedCannotTransitionToSuccess() {
        assertFalse(
                PaymentStatus.FAILED.canTransitionTo(
                        PaymentStatus.SUCCESS
                )
        );
    }

    @Test
    void successCannotTransitionToProcessing() {
        assertFalse(
                PaymentStatus.SUCCESS.canTransitionTo(
                        PaymentStatus.PROCESSING
                )
        );
    }

    @Test
    void failedCannotTransitionToProcessing() {
        assertFalse(
                PaymentStatus.FAILED.canTransitionTo(
                        PaymentStatus.PROCESSING
                )
        );
    }
}
