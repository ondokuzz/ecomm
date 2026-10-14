package com.ecomm.fulfillmentshipping;

import static org.awaitility.Awaitility.await;

import com.ecomm.fulfillmentshipping.adapter.out.carrier.CarrierWebhookDelivery;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Test-only: takes the place of the mock carrier's HTTP delivery and keeps each signed Carrier
 * webhook it would have sent, so a test decides whether, when and how often each one arrives.
 */
final class RecordedCarrierWebhooks implements CarrierWebhookDelivery {

  private final List<SignedWebhook> sent = new CopyOnWriteArrayList<>();

  @Override
  public void deliver(SignedWebhook webhook) {
    sent.add(webhook);
  }

  /**
   * The three webhooks the Carrier sends about the Shipment it knows by {@code trackingNumber}, in
   * the order it sent them: in transit, out for delivery, delivered.
   */
  List<SignedWebhook> about(String trackingNumber) {
    return await()
        .atMost(Duration.ofSeconds(10))
        .until(
            () ->
                sent.stream().filter(w -> w.body().contains("\"" + trackingNumber + "\"")).toList(),
            found -> found.size() == 3);
  }
}
