package com.ecomm.payment.adapter.out.gateway;

import com.ecomm.payment.application.port.out.PaymentGatewayPort;
import com.ecomm.payment.domain.AuthorizationRequest;
import com.ecomm.payment.domain.GatewayAuthorization;
import com.ecomm.payment.domain.PaymentGatewayUnavailableException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.util.Collections;
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
 * Stands in for a real gateway, reading the payment method as a test token, each answer with a
 * fresh {@code mock-} reference: {@code tok_approve} authorizes; {@code tok_decline} and {@code
 * tok_insufficient_funds} decline, as {@code card_declined} and {@code insufficient_funds}; {@code
 * tok_gateway_error} fails to answer; any other token declines as {@code unknown_payment_method}.
 * Every void succeeds.
 *
 * <p>{@code tok_async_approve} and {@code tok_async_decline} stand for a card the Customer's bank
 * confirms later: they are answered as pending, and {@code webhookDelay} later the mock sends a
 * Gateway webhook settling the authorization as approved, or declined as {@code card_declined},
 * signed with the secret it shares with Payment and handed to a {@link WebhookDelivery}. A webhook
 * still waiting to be sent when the service stops is lost.
 *
 * <p>As a real gateway does, it answers a repeated idempotency key with its first answer and
 * reference, for the most recent {@value #REMEMBERED_KEYS} keys, until the service restarts. A
 * failure to answer isn't remembered, so a retry can succeed.
 */
class MockPaymentGatewayAdapter implements PaymentGatewayPort, AutoCloseable {

  private static final int REMEMBERED_KEYS = 10_000;

  private final Map<String, GatewayAuthorization> authorizations = remembered();
  private final Map<String, String> voids = remembered();

  private final WebhookDelivery webhooks;
  private final Duration webhookDelay;
  private final byte[] webhookSecret;
  private final JsonMapper json;
  private final ScheduledExecutorService scheduler =
      Executors.newSingleThreadScheduledExecutor(
          runnable ->
              Thread.ofPlatform().daemon().name("mock-gateway-webhooks").unstarted(runnable));

  MockPaymentGatewayAdapter(
      WebhookDelivery webhooks, Duration webhookDelay, String webhookSecret, JsonMapper json) {
    this.webhooks = webhooks;
    this.webhookDelay = webhookDelay;
    this.webhookSecret = webhookSecret.getBytes(StandardCharsets.UTF_8);
    this.json = json;
  }

  @Override
  public GatewayAuthorization authorize(AuthorizationRequest request, String idempotencyKey) {
    var answered = authorizations.get(idempotencyKey);
    if (answered != null) {
      return answered;
    }
    var reference = "mock-" + UUID.randomUUID();
    GatewayAuthorization answer =
        switch (request.paymentMethod()) {
          case "tok_approve" -> new GatewayAuthorization.Approved(reference);
          case "tok_decline" -> new GatewayAuthorization.Declined(reference, "card_declined");
          case "tok_insufficient_funds" ->
              new GatewayAuthorization.Declined(reference, "insufficient_funds");
          case "tok_async_approve", "tok_async_decline" ->
              new GatewayAuthorization.Pending(reference);
          case "tok_gateway_error" ->
              throw new PaymentGatewayUnavailableException("The mock gateway failed, as asked");
          default -> new GatewayAuthorization.Declined(reference, "unknown_payment_method");
        };
    var first = authorizations.putIfAbsent(idempotencyKey, answer);
    if (first != null) {
      return first;
    }
    if (answer instanceof GatewayAuthorization.Pending) {
      var webhook =
          request.paymentMethod().equals("tok_async_approve")
              ? new Webhook(newEventId(), reference, "APPROVED", null)
              : new Webhook(newEventId(), reference, "DECLINED", "card_declined");
      scheduler.schedule(
          () -> webhooks.deliver(signed(webhook)), webhookDelay.toMillis(), TimeUnit.MILLISECONDS);
    }
    return answer;
  }

  /** A Gateway webhook's body, as Payment's {@code /webhooks/gateway} reads it. */
  private record Webhook(String eventId, String reference, String outcome, String declineReason) {}

  private static String newEventId() {
    return "evt_" + UUID.randomUUID();
  }

  private WebhookDelivery.SignedWebhook signed(Webhook webhook) {
    var body = json.writeValueAsString(webhook);
    try {
      var mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(webhookSecret, "HmacSHA256"));
      var signature = mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
      return new WebhookDelivery.SignedWebhook(
          body, "sha256=" + HexFormat.of().formatHex(signature));
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("HMAC-SHA256 is unavailable", e);
    }
  }

  @Override
  public void close() {
    scheduler.shutdownNow();
  }

  @Override
  public String voidAuthorization(String authorizationReference, String idempotencyKey) {
    return voids.computeIfAbsent(idempotencyKey, key -> "mock-void-" + UUID.randomUUID());
  }

  /** The most recently added {@value #REMEMBERED_KEYS} entries, safe to share between threads. */
  private static <V> Map<String, V> remembered() {
    return Collections.synchronizedMap(
        new LinkedHashMap<>() {
          @Override
          protected boolean removeEldestEntry(Map.Entry<String, V> eldest) {
            return size() > REMEMBERED_KEYS;
          }
        });
  }
}
