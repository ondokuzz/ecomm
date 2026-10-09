package com.ecomm.payment.adapter.in.web;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** {@code payment.webhooks.secret}: the secret Payment shares with the gateway, never defaulted. */
@ConfigurationProperties("payment.webhooks")
record WebhookProperties(String secret) {}
