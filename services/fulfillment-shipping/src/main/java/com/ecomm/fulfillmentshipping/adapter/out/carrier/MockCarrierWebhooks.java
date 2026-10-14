package com.ecomm.fulfillmentshipping.adapter.out.carrier;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import tools.jackson.databind.json.JsonMapper;

/**
 * The mock carrier's Carrier webhooks: for each Shipment it books, {@code IN_TRANSIT}, {@code
 * OUT_FOR_DELIVERY} and {@code DELIVERED}, each sent its delay after booking and saying it happened
 * then, signed with the secret the Carrier shares with the service and handed to a {@link
 * CarrierWebhookDelivery}. A webhook still waiting to be sent when the service stops is lost.
 */
class MockCarrierWebhooks implements AutoCloseable {

  /** The header a Carrier webhook's signature travels in: {@code sha256=<hex HMAC-SHA256>}. */
  static final String SIGNATURE_HEADER = "Carrier-Signature";

  private final CarrierWebhookDelivery delivery;
  private final Map<String, Duration> delays;
  private final byte[] secret;
  private final JsonMapper json;
  private final ScheduledExecutorService scheduler =
      Executors.newSingleThreadScheduledExecutor(
          runnable ->
              Thread.ofPlatform().daemon().name("mock-carrier-webhooks").unstarted(runnable));

  /**
   * @param inTransit how long after booking the parcel is in transit
   * @param outForDelivery how long after booking it is out for delivery
   * @param delivered how long after booking it is delivered
   */
  MockCarrierWebhooks(
      CarrierWebhookDelivery delivery,
      Duration inTransit,
      Duration outForDelivery,
      Duration delivered,
      String secret,
      JsonMapper json) {
    if (secret == null || secret.isBlank()) {
      throw new IllegalStateException(
          "ecomm.fulfillment-shipping.carrier-webhooks.secret is required: compose gives it as a"
              + " secret");
    }
    this.delivery = delivery;
    this.delays = new LinkedHashMap<>();
    delays.put("IN_TRANSIT", inTransit);
    delays.put("OUT_FOR_DELIVERY", outForDelivery);
    delays.put("DELIVERED", delivered);
    this.secret = secret.getBytes(StandardCharsets.UTF_8);
    this.json = json;
  }

  /** A Carrier webhook's body, as {@code /webhooks/carrier} reads it. */
  private record Webhook(String eventId, String trackingNumber, String status, String occurredAt) {}

  /** Sends each of the Shipment's Tracking events, its delay after {@code bookedAt}. */
  void scheduleFor(String trackingNumber, Instant bookedAt) {
    delays.forEach(
        (status, delay) -> {
          var webhook =
              new Webhook(
                  "trk_" + UUID.randomUUID(),
                  trackingNumber,
                  status,
                  bookedAt.plus(delay).toString());
          scheduler.schedule(
              () -> delivery.deliver(signed(webhook)), delay.toMillis(), TimeUnit.MILLISECONDS);
        });
  }

  private CarrierWebhookDelivery.SignedWebhook signed(Webhook webhook) {
    var body = json.writeValueAsString(webhook);
    try {
      var mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret, "HmacSHA256"));
      var signature = mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
      return new CarrierWebhookDelivery.SignedWebhook(
          body, "sha256=" + HexFormat.of().formatHex(signature));
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("HMAC-SHA256 is unavailable", e);
    }
  }

  @Override
  public void close() {
    scheduler.shutdownNow();
  }
}
