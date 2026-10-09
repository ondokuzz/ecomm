package com.ecomm.payment;

import static org.awaitility.Awaitility.await;

import com.ecomm.payment.adapter.out.gateway.WebhookDelivery;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Test-only: takes the place of the mock gateway's HTTP delivery and keeps each signed webhook it
 * would have sent, so a test decides whether, when and how often each one reaches Payment.
 */
final class RecordedWebhooks implements WebhookDelivery {

  private final List<SignedWebhook> sent = new CopyOnWriteArrayList<>();

  @Override
  public void deliver(SignedWebhook webhook) {
    sent.add(webhook);
  }

  /** The webhook the gateway sent about the authorization it knows by {@code reference}. */
  SignedWebhook about(String reference) {
    return await()
        .atMost(Duration.ofSeconds(10))
        .until(
            () -> sent.stream().filter(w -> w.body().contains("\"" + reference + "\"")).toList(),
            found -> !found.isEmpty())
        .getFirst();
  }
}
