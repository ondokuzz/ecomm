package com.ecomm.commons.idempotency;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * How long an idempotency key is kept, matching Temporal's retention of a workflow's history, and
 * how often older keys are pruned.
 */
@ConfigurationProperties("ecomm.idempotency")
public record IdempotencyProperties(
    @DefaultValue("7d") Duration keepFor, @DefaultValue("1d") Duration pruneEvery) {}
