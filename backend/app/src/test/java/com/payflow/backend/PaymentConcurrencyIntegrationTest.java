
package com.payflow.backend;

import com.payflow.backend.entity.Payment;
import com.payflow.backend.repository.PaymentRepository;
import com.payflow.backend.service.PaymentService;

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
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class PaymentConcurrencyIntegrationTest {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private PaymentRepository paymentRepository;

    @Test
    void concurrentRequestsWithSameKeyShouldCreateOnlyOnePayment()
            throws Exception {

        int numberOfRequests = 10;

        // Unique key ensures this test can be run repeatedly.
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

            // Submit all 10 requests.
            for (int i = 0; i < numberOfRequests; i++) {

                Future<Payment> future = executor.submit(() -> {

                    ready.countDown();

                    // Wait until all threads are ready.
                    if (!start.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException(
                                "Timed out waiting for concurrent start"
                        );
                    }

                    return paymentService.createPayment(
                            amount,
                            currency,
                            idempotencyKey
                    );
                });

                futures.add(future);
            }

            // Ensure all threads are ready.
            assertTrue(
                    ready.await(10, TimeUnit.SECONDS),
                    "All requests should be ready"
            );

            // Release all requests together.
            start.countDown();

            Set<UUID> paymentIds = new HashSet<>();

            // Collect all responses.
            for (Future<Payment> future : futures) {

                Payment payment =
                        future.get(30, TimeUnit.SECONDS);

                paymentIds.add(payment.getId());
            }

            // All requests should return the same payment ID.
            assertEquals(
                    1,
                    paymentIds.size(),
                    "All requests should return the same payment"
            );

            // Verify the actual PostgreSQL database.
            long databaseCount =
                    paymentRepository.countByIdempotencyKey(
                            idempotencyKey
                    );

            assertEquals(
                    1,
                    databaseCount,
                    "Only one payment should exist in PostgreSQL"
            );

            System.out.println("==================================");
            System.out.println("CONCURRENCY TEST PASSED");
            System.out.println("Total requests: " + numberOfRequests);
            System.out.println("Unique payment IDs: " + paymentIds.size());
            System.out.println("Database records: " + databaseCount);
            System.out.println("==================================");

        } finally {
            executor.shutdownNow();
        }
    }
}
