package com.ecomm.commons.events.kafka;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * How often a listener's failure is retried before the event is sent to its consumer group's
 * dead-letter topic: {@code retries} more attempts, waiting {@code initial-backoff} before the
 * first and twice as long before each one after. By default, 5 more attempts, after 0.5, 1, 2, 4
 * and 8 seconds. Retries block the partition, which keeps one aggregate's events in order.
 */
@ConfigurationProperties("ecomm.events.consumer")
public record ConsumerProperties(
    @DefaultValue("5") int retries, @DefaultValue("500ms") Duration initialBackoff) {}
