package com.ecomm.payment.adapter.out.gateway;

/**
 * How the mock gateway sends its webhooks: over HTTP to Payment's own endpoint on the stack, or to
 * whatever a test puts in its place, to deliver, repeat, reorder or withhold them.
 */
public interface WebhookDelivery {

  /**
   * A webhook as it goes over the wire: its JSON body, and the body's HMAC-SHA256 signature as the
   * {@code Gateway-Signature} header carries it.
   */
  record SignedWebhook(String body, String signature) {}

  void deliver(SignedWebhook webhook);
}
