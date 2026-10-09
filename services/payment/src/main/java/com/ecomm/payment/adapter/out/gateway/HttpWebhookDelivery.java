package com.ecomm.payment.adapter.out.gateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Posts each webhook to {@code url}, or, when none is set, to Payment's own {@code
 * /webhooks/gateway} on the port it listens on, as a real gateway would from outside. A delivery
 * that fails is logged and not sent again: the checkout Saga voids a Payment that stays pending.
 */
class HttpWebhookDelivery implements WebhookDelivery {

  private static final Logger log = LoggerFactory.getLogger(HttpWebhookDelivery.class);

  private final RestClient http;
  private final String url;
  private final Environment environment;

  HttpWebhookDelivery(RestClient http, String url, Environment environment) {
    this.http = http;
    this.url = url;
    this.environment = environment;
  }

  @Override
  public void deliver(SignedWebhook webhook) {
    try {
      http.post()
          .uri(target())
          .header("Gateway-Signature", webhook.signature())
          .contentType(MediaType.APPLICATION_JSON)
          .body(webhook.body())
          .retrieve()
          .toBodilessEntity();
    } catch (RestClientException e) {
      log.warn("The mock gateway's webhook wasn't delivered: {}", webhook.body(), e);
    }
  }

  /** Payment's own port is known only once it listens, so it is read at each delivery. */
  private String target() {
    if (url != null && !url.isBlank()) {
      return url;
    }
    return "http://localhost:"
        + environment.getProperty("local.server.port", "8080")
        + "/webhooks/gateway";
  }
}
