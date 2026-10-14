package com.ecomm.fulfillmentshipping.adapter.out.carrier;

/**
 * How the mock carrier sends its Carrier webhooks: over HTTP to the service's own endpoint on the
 * stack, or to whatever a test puts in its place, to deliver, repeat, reorder or withhold them.
 */
public interface CarrierWebhookDelivery {

  /**
   * A webhook as it goes over the wire: its JSON body, and the body's HMAC-SHA256 signature as the
   * {@code Carrier-Signature} header carries it.
   */
  record SignedWebhook(String body, String signature) {}

  void deliver(SignedWebhook webhook);
}
