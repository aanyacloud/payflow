
package com.payflow.backend.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@ConditionalOnProperty(
        name = "payflow.outbox.publisher.enabled",
        havingValue = "true"
)
public class OutboxPublisherSchedulingConfig {
}
