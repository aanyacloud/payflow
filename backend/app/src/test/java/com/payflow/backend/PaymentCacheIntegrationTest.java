
package com.payflow.backend;

import com.payflow.backend.config.RedisCacheConfig;
import com.payflow.backend.dto.UpdatePaymentStatusRequest;
import com.payflow.backend.entity.Payment;
import com.payflow.backend.entity.PaymentStatus;
import com.payflow.backend.entity.User;
import com.payflow.backend.service.PaymentService;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
@Execution(ExecutionMode.SAME_THREAD)
@ContextConfiguration(classes = {
        PaymentCacheIntegrationTest.TestConfig.class,
        RedisCacheConfig.class
})
class PaymentCacheIntegrationTest {

    @Configuration
    static class TestConfig {

        @Bean
        RedisConnectionFactory redisConnectionFactory() {
            return new LettuceConnectionFactory(
                    "localhost",
                    6379
            );
        }

        @Bean
        PaymentService paymentService() {
            return mock(PaymentService.class);
        }

        @Bean
        PaymentController paymentController(
                PaymentService paymentService
        ) {
            return new PaymentController(paymentService);
        }
    }

    @Autowired
    private PaymentController paymentController;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private CacheManager cacheManager;

    private UUID userId;
    private UUID paymentId;
    private User user;
    private Jwt jwt;

    @BeforeEach
    void setUp() {

        clearPaymentCache();

        reset(paymentService);

        userId = UUID.randomUUID();
        paymentId = UUID.randomUUID();

        user = new User(
                "cache-test-" + userId + "@payflow.com",
                "password",
                "USER"
        );

        jwt = createJwt(userId);
    }

    @AfterEach
    void cleanUp() {
        clearPaymentCache();
        reset(paymentService);
    }

    private void clearPaymentCache() {

        Cache cache = cacheManager.getCache("payments");

        if (cache != null) {
            cache.clear();
        }
    }

    private Jwt createJwt(UUID userId) {

        return Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .subject(userId.toString())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
    }

    private Payment createPayment(String status) {

        return new Payment(
                user,
                new BigDecimal("500.00"),
                "INR",
                status
        );
    }

    @Test
    void shouldCachePaymentAfterFirstRequest() {

        Payment payment = createPayment("PENDING");

        when(paymentService.getPaymentById(userId, paymentId))
                .thenReturn(payment);

        var firstResponse = paymentController.getPayment(
                paymentId,
                jwt
        );

        var secondResponse = paymentController.getPayment(
                paymentId,
                jwt
        );

        assertEquals(
                firstResponse.status(),
                secondResponse.status()
        );

        assertEquals("PENDING", secondResponse.status());

        verify(paymentService, times(1))
                .getPaymentById(userId, paymentId);
    }

    @Test
    void shouldMaintainSeparateCacheForDifferentUsers() {

        UUID anotherUserId = UUID.randomUUID();

        Jwt anotherJwt = createJwt(anotherUserId);

        Payment payment = createPayment("PENDING");

        when(paymentService.getPaymentById(userId, paymentId))
                .thenReturn(payment);

        when(paymentService.getPaymentById(
                anotherUserId,
                paymentId
        )).thenReturn(payment);

        paymentController.getPayment(paymentId, jwt);

        paymentController.getPayment(paymentId, anotherJwt);

        verify(paymentService, times(1))
                .getPaymentById(userId, paymentId);

        verify(paymentService, times(1))
                .getPaymentById(anotherUserId, paymentId);
    }

    @Test
    void shouldEvictPaymentCacheAfterSuccessfulStatusUpdate() {

        Payment pendingPayment = createPayment("PENDING");

        Payment successfulPayment = createPayment("SUCCESS");

        when(paymentService.getPaymentById(userId, paymentId))
                .thenReturn(
                        pendingPayment,
                        successfulPayment
                );

        when(paymentService.updatePaymentStatus(
                userId,
                paymentId,
                PaymentStatus.SUCCESS
        )).thenReturn(successfulPayment);

        var firstResponse = paymentController.getPayment(
                paymentId,
                jwt
        );

        assertEquals("PENDING", firstResponse.status());

        paymentController.updatePaymentStatus(
                paymentId,
                new UpdatePaymentStatusRequest(
                        PaymentStatus.SUCCESS
                ),
                jwt
        );

        var updatedResponse = paymentController.getPayment(
                paymentId,
                jwt
        );

        assertEquals("SUCCESS", updatedResponse.status());

        verify(paymentService, times(2))
                .getPaymentById(userId, paymentId);
    }
}
