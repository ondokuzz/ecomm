package com.ecomm.fulfillmentshipping.adapter.out.carrier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Posts each Carrier webhook to {@code url}, or, when none is set, to the service's own {@code
 * /webhooks/carrier} on the port it listens on, as a Carrier would from outside. A delivery that
 * fails is logged and not sent again; the fulfillment Saga's wait for delivery ends after its
 * deadline.
 */
class HttpCarrierWebhookDelivery implements CarrierWebhookDelivery {

  private static final Logger log = LoggerFactory.getLogger(HttpCarrierWebhookDelivery.class);

  private final RestClient http;
  private final String url;
  private final Environment environment;

  HttpCarrierWebhookDelivery(RestClient http, String url, Environment environment) {
    this.http = http;
    this.url = url;
    this.environment = environment;
  }

  @Override
  public void deliver(SignedWebhook webhook) {
    try {
      http.post()
          .uri(target())
          .header(MockCarrierWebhooks.SIGNATURE_HEADER, webhook.signature())
          .contentType(MediaType.APPLICATION_JSON)
          .body(webhook.body())
          .retrieve()
          .toBodilessEntity();
    } catch (RestClientException e) {
      log.warn("The mock carrier's webhook wasn't delivered: {}", webhook.body(), e);
    }
  }

  /** The service's own port is known only once it listens, so it is read at each delivery. */
  private String target() {
    if (url != null && !url.isBlank()) {
      return url;
    }
    return "http://localhost:"
        + environment.getProperty("local.server.port", "8080")
        + "/webhooks/carrier";
  }
}
