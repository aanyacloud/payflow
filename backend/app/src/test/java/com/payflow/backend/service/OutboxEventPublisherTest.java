
package com.payflow.backend.service;

import com.payflow.backend.entity.OutboxEvent;
import com.payflow.backend.events.PaymentCreatedEvent;
import com.payflow.backend.repository.OutboxEventRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxEventPublisherTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private KafkaTemplate<String, PaymentCreatedEvent> kafkaTemplate;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private OutboxEventPublisher outboxEventPublisher;

    private UUID eventId;
    private UUID paymentId;
    private UUID userId;

    private PaymentCreatedEvent paymentEvent;
    private OutboxEvent outboxEvent;

    private final String payload = """
            {
                "eventId": "11111111-1111-1111-1111-111111111111",
                "paymentId": "22222222-2222-2222-2222-222222222222",
                "userId": "33333333-3333-3333-3333-333333333333",
                "amount": 100.00,
                "currency": "INR",
                "status": "PENDING",
                "occurredAt": "2026-11-19T10:00:00Z"
            }
            """;

    @BeforeEach
    void setUp() {

        eventId = UUID.fromString(
                "11111111-1111-1111-1111-111111111111"
        );

        paymentId = UUID.fromString(
                "22222222-2222-2222-2222-222222222222"
        );

        userId = UUID.fromString(
                "33333333-3333-3333-3333-333333333333"
        );

        paymentEvent = new PaymentCreatedEvent(
                eventId,
                paymentId,
                userId,
                new BigDecimal("100.00"),
                "INR",
                "PENDING",
                Instant.parse("2026-11-19T10:00:00Z")
        );

        outboxEvent = new OutboxEvent(
                eventId,
                "PAYMENT",
                paymentId,
                "PAYMENT_CREATED",
                payload,
                Instant.now(),
                null,
                0,
                null
        );
    }

    @Test
    void shouldPublishEventAndMarkItAsPublished() throws Exception {

        when(outboxEventRepository.claimPendingEvents(anyInt()))
                .thenReturn(List.of(outboxEvent));

        when(objectMapper.readValue(
                eq(payload),
                eq(PaymentCreatedEvent.class)
        )).thenReturn(paymentEvent);

        CompletableFuture<SendResult<String, PaymentCreatedEvent>>
                successfulFuture = CompletableFuture.completedFuture(null);

        when(kafkaTemplate.send(
                eq("payment-events"),
                eq(paymentId.toString()),
                eq(paymentEvent)
        )).thenReturn(successfulFuture);

        assertDoesNotThrow(
                () -> outboxEventPublisher.publishPendingEvents()
        );

        verify(outboxEventRepository).claimPendingEvents(anyInt());

        verify(kafkaTemplate).send(
                "payment-events",
                paymentId.toString(),
                paymentEvent
        );

        verify(outboxEventRepository).markPublished(
                eq(eventId),
                any(Instant.class)
        );

        verify(outboxEventRepository, never())
                .recordFailure(any(), anyString());
    }

    @Test
    void shouldRecordFailureWhenKafkaPublishingFails() throws Exception {

        when(outboxEventRepository.claimPendingEvents(anyInt()))
                .thenReturn(List.of(outboxEvent));

        when(objectMapper.readValue(
                eq(payload),
                eq(PaymentCreatedEvent.class)
        )).thenReturn(paymentEvent);

        CompletableFuture<SendResult<String, PaymentCreatedEvent>>
                failedFuture = new CompletableFuture<>();

        failedFuture.completeExceptionally(
                new RuntimeException("Kafka unavailable")
        );

        when(kafkaTemplate.send(
                eq("payment-events"),
                eq(paymentId.toString()),
                eq(paymentEvent)
        )).thenReturn(failedFuture);

        assertDoesNotThrow(
                () -> outboxEventPublisher.publishPendingEvents()
        );

        verify(kafkaTemplate).send(
                "payment-events",
                paymentId.toString(),
                paymentEvent
        );

        verify(outboxEventRepository).recordFailure(
                eq(eventId),
                contains("Kafka unavailable")
        );

        verify(outboxEventRepository, never())
                .markPublished(any(), any());
    }

    @Test
    void shouldRecordFailureWhenPayloadIsInvalid() throws Exception {

        when(outboxEventRepository.claimPendingEvents(anyInt()))
                .thenReturn(List.of(outboxEvent));

        when(objectMapper.readValue(
                eq(payload),
                eq(PaymentCreatedEvent.class)
        )).thenThrow(
                new RuntimeException("Invalid JSON payload")
        );

        assertDoesNotThrow(
                () -> outboxEventPublisher.publishPendingEvents()
        );

        verify(objectMapper).readValue(
                payload,
                PaymentCreatedEvent.class
        );

        verify(kafkaTemplate, never()).send(
                anyString(),
                anyString(),
                any(PaymentCreatedEvent.class)
        );

        verify(outboxEventRepository).recordFailure(
                eq(eventId),
                contains("Invalid JSON payload")
        );

        verify(outboxEventRepository, never())
                .markPublished(any(), any());
    }

    @Test
    void shouldDoNothingWhenNoPendingEventsExist() {

        when(outboxEventRepository.claimPendingEvents(anyInt()))
                .thenReturn(List.of());

        assertDoesNotThrow(
                () -> outboxEventPublisher.publishPendingEvents()
        );

        verifyNoInteractions(kafkaTemplate, objectMapper);

        verify(outboxEventRepository, never())
                .markPublished(any(), any());

        verify(outboxEventRepository, never())
                .recordFailure(any(), anyString());
    }
}
