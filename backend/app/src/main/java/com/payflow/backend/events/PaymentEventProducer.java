
package com.payflow.backend.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class PaymentEventProducer {

    private static final Logger log =
            LoggerFactory.getLogger(PaymentEventProducer.class);

    private static final String TOPIC = "payment-events";

    private final KafkaTemplate<String, PaymentCreatedEvent> kafkaTemplate;

    public PaymentEventProducer(
            KafkaTemplate<String, PaymentCreatedEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishPaymentCreated(PaymentCreatedEvent event) {

        String key = event.paymentId().toString();

        kafkaTemplate.send(TOPIC, key, event)
                .whenComplete((result, exception) -> {

                    if (exception != null) {
                        log.error(
                                "Failed to publish payment event: {}",
                                event.paymentId(),
                                exception
                        );
                    } else {
                        log.info(
                                "Payment event published successfully. Payment ID: {}",
                                event.paymentId()
                        );
                    }
                });
    }
}
