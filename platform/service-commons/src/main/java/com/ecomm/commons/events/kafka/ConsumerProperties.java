package com.ecomm.commons.events.kafka;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * How often a listener's failure is retried before the event is logged and skipped: {@code retries}
 * more attempts, waiting {@code initial-backoff} before the first and twice as long before each one
 * after.
 */
@ConfigurationProperties("ecomm.events.consumer")
public record ConsumerProperties(
    @DefaultValue("3") int retries, @DefaultValue("500ms") Duration initialBackoff) {}
