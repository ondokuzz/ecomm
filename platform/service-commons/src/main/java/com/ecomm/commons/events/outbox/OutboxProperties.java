package com.ecomm.commons.events.outbox;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** How often a publication whose send failed is tried again. */
@ConfigurationProperties("ecomm.events.outbox")
public record OutboxProperties(@DefaultValue("30s") Duration resubmitFailedEvery) {}
