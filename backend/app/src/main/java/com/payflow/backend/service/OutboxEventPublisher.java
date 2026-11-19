
package com.payflow.backend.service;

import com.payflow.backend.entity.OutboxEvent;
import com.payflow.backend.events.PaymentCreatedEvent;
import com.payflow.backend.repository.OutboxEventRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class OutboxEventPublisher {

    private static final Logger log =
            LoggerFactory.getLogger(OutboxEventPublisher.class);

    private static final String TOPIC = "payment-events";

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, PaymentCreatedEvent> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Value("${payflow.outbox.publisher.batch-size:50}")
    private int batchSize;

    public OutboxEventPublisher(
            OutboxEventRepository outboxEventRepository,
            KafkaTemplate<String, PaymentCreatedEvent> kafkaTemplate,
            ObjectMapper objectMapper
    ) {
        this.outboxEventRepository = outboxEventRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    @Scheduled(
            fixedDelayString =
                    "${payflow.outbox.publisher.fixed-delay:5000}"
    )
    public void publishPendingEvents() {

        List<OutboxEvent> events =
                outboxEventRepository.claimPendingEvents(batchSize);

        if (events.isEmpty()) {
            return;
        }

        log.info("Claimed {} pending outbox events", events.size());

        for (OutboxEvent event : events) {
            publishEvent(event);
        }
    }

    private void publishEvent(OutboxEvent event) {

        try {
            PaymentCreatedEvent paymentEvent =
                    objectMapper.readValue(
                            event.payload(),
                            PaymentCreatedEvent.class
                    );

            String key = paymentEvent.paymentId().toString();

            // Wait for Kafka acknowledgement before marking as published.
            kafkaTemplate.send(
                    TOPIC,
                    key,
                    paymentEvent
            ).get(30, TimeUnit.SECONDS);

            outboxEventRepository.markPublished(
                    event.eventId(),
                    Instant.now()
            );

            log.info(
                    "Published payment event successfully. Event ID: {}, Payment ID: {}",
                    event.eventId(),
                    paymentEvent.paymentId()
            );

        } catch (Exception exception) {

            String errorMessage = exception.getMessage();

            if (errorMessage == null || errorMessage.isBlank()) {
                errorMessage = exception.getClass().getSimpleName();
            }

            log.error(
                    "Failed to publish outbox event: {}",
                    event.eventId(),
                    exception
            );

            try {
                outboxEventRepository.recordFailure(
                        event.eventId(),
                        errorMessage
                );

            } catch (Exception recordingException) {

                log.error(
                        "Could not record failure for outbox event: {}",
                        event.eventId(),
                        recordingException
                );
            }
        }
    }
}
