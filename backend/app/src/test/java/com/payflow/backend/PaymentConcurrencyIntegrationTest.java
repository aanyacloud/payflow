
package com.payflow.backend;

import com.payflow.backend.entity.Payment;
import com.payflow.backend.entity.PaymentStatus;
import com.payflow.backend.entity.Refund;
import com.payflow.backend.entity.RefundStatus;
import com.payflow.backend.entity.User;
import com.payflow.backend.exception.InvalidPaymentStatusTransitionException;
import com.payflow.backend.repository.PaymentRepository;
import com.payflow.backend.repository.RefundRepository;
import com.payflow.backend.repository.UserRepository;
import com.payflow.backend.service.PaymentService;
import com.payflow.backend.service.RefundService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:5432/payflow_test",
        "spring.datasource.username=payflow",
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "logging.level.org.hibernate.SQL=DEBUG",
        "logging.level.org.springframework.transaction=TRACE"
})
class PaymentConcurrencyIntegrationTest {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private RefundService refundService;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private RefundRepository refundRepository;

    @Autowired
    private UserRepository userRepository;

    private UUID userId;

    @BeforeEach
    void setUp() {

        User user = new User(
                "concurrency-" + UUID.randomUUID() + "@payflow.com",
                "test-password",
                "USER"
        );

        userId = userRepository.save(user).getId();
    }

    // TEST 1: Concurrent requests with the same idempotency key
    @Test
    void concurrentRequestsWithSameKeyShouldCreateOnlyOnePayment()
            throws Exception {

        int numberOfRequests = 10;

        String idempotencyKey =
                "concurrency-test-" + UUID.randomUUID();

        BigDecimal amount = new BigDecimal("799.99");
        String currency = "INR";

        ExecutorService executor =
                Executors.newFixedThreadPool(numberOfRequests);

        CountDownLatch ready =
                new CountDownLatch(numberOfRequests);

        CountDownLatch start =
                new CountDownLatch(1);

        try {
            List<Future<Payment>> futures = new ArrayList<>();

            for (int i = 0; i < numberOfRequests; i++) {

                Future<Payment> future = executor.submit(() -> {

                    ready.countDown();

                    if (!start.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException(
                                "Timed out waiting for concurrent start"
                        );
                    }

                    return paymentService.createPayment(
                            userId,
                            amount,
                            currency,
                            idempotencyKey
                    );
                });

                futures.add(future);
            }

            assertTrue(
                    ready.await(10, TimeUnit.SECONDS),
                    "All requests should be ready"
            );

            start.countDown();

            Set<UUID> paymentIds = new HashSet<>();

            for (Future<Payment> future : futures) {

                Payment payment =
                        future.get(30, TimeUnit.SECONDS);

                paymentIds.add(payment.getId());
            }

            assertEquals(
                    1,
                    paymentIds.size(),
                    "All requests should return the same payment"
            );

            long databaseCount =
                    paymentRepository.countByUser_IdAndIdempotencyKey(
                            userId,
                            idempotencyKey
                    );

            assertEquals(
                    1,
                    databaseCount,
                    "Only one payment should exist for this user and key"
            );

            System.out.println("==================================");
            System.out.println("PAYMENT IDEMPOTENCY TEST PASSED");
            System.out.println("Total requests: " + numberOfRequests);
            System.out.println("Unique payment IDs: " + paymentIds.size());
            System.out.println("Database records: " + databaseCount);
            System.out.println("==================================");

        } finally {
            executor.shutdownNow();
        }
    }

    // TEST 2: Concurrent refund status updates
    @Test
    void concurrentRefundStatusUpdatesShouldSerializeValidTransitions()
            throws Exception {

        // 1. Create a successful payment.
        String paymentKey =
                "refund-concurrency-payment-" + UUID.randomUUID();

        Payment payment = paymentService.createPayment(
                userId,
                new BigDecimal("1000.00"),
                "INR",
                paymentKey
        );

        paymentService.updatePaymentStatus(
                userId,
                payment.getId(),
                PaymentStatus.PROCESSING
        );

        paymentService.updatePaymentStatus(
                userId,
                payment.getId(),
                PaymentStatus.SUCCESS
        );

        // 2. Create one PENDING refund.
        String refundKey =
                "refund-concurrency-" + UUID.randomUUID();

        Refund refund = refundService.createRefund(
                userId,
                payment.getId(),
                new BigDecimal("500.00"),
                refundKey
        );

        assertEquals(
                RefundStatus.PENDING.name(),
                refund.getStatus()
        );

        // 3. Prepare two simultaneous status updates.
        ExecutorService executor =
                Executors.newFixedThreadPool(2);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try {
            Future<Boolean> processingFuture = executor.submit(() -> {

                ready.countDown();

                if (!start.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException(
                            "Timed out waiting for concurrent start"
                    );
                }

                try {
                    refundService.updateRefundStatus(
                            userId,
                            payment.getId(),
                            refund.getId(),
                            RefundStatus.PROCESSING
                    );

                    return true;

                } catch (InvalidPaymentStatusTransitionException exception) {
                    return false;
                }
            });

            Future<Boolean> failedFuture = executor.submit(() -> {

                ready.countDown();

                if (!start.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException(
                            "Timed out waiting for concurrent start"
                    );
                }

                try {
                    refundService.updateRefundStatus(
                            userId,
                            payment.getId(),
                            refund.getId(),
                            RefundStatus.FAILED
                    );

                    return true;

                } catch (InvalidPaymentStatusTransitionException exception) {
                    return false;
                }
            });

            // Wait until both threads are ready.
            assertTrue(
                    ready.await(10, TimeUnit.SECONDS),
                    "Both refund updates should be ready"
            );

            // Release both updates together.
            start.countDown();

            boolean processingSucceeded =
                    processingFuture.get(30, TimeUnit.SECONDS);

            boolean failedSucceeded =
                    failedFuture.get(30, TimeUnit.SECONDS);

            // At least one valid transition must succeed.
            assertTrue(
                    processingSucceeded || failedSucceeded,
                    "At least one concurrent refund update should succeed"
            );

            // 4. Verify the final state in PostgreSQL.
            Refund finalRefund = refundRepository
                    .findByIdAndPayment_Id(
                            refund.getId(),
                            payment.getId()
                    )
                    .orElseThrow();

            String expectedFinalStatus =
                    processingSucceeded
                            ? (failedSucceeded
                                    ? RefundStatus.FAILED.name()
                                    : RefundStatus.PROCESSING.name())
                            : RefundStatus.FAILED.name();

            assertEquals(
                    expectedFinalStatus,
                    finalRefund.getStatus(),
                    "Final refund status must reflect the valid serialized transitions"
            );

            // 5. Verify every successful transition has one history entry.
            var history = refundService.getRefundStatusHistory(
                    userId,
                    payment.getId(),
                    refund.getId()
            );

            int successfulUpdates =
                    (processingSucceeded ? 1 : 0)
                            + (failedSucceeded ? 1 : 0);

            assertEquals(
                    successfulUpdates,
                    history.size(),
                    "Every successful refund transition must have one history entry"
            );

            System.out.println("==================================");
            System.out.println("REFUND CONCURRENCY TEST PASSED");
            System.out.println("Payment ID: " + payment.getId());
            System.out.println("Refund ID: " + refund.getId());
            System.out.println("PROCESSING update succeeded: " + processingSucceeded);
            System.out.println("FAILED update succeeded: " + failedSucceeded);
            System.out.println("Final refund status: " + finalRefund.getStatus());
            System.out.println("History records: " + history.size());
            System.out.println("==================================");

        } finally {
            executor.shutdownNow();
        }
    }
}
