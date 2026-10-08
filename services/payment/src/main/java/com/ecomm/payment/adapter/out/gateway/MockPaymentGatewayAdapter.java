package com.ecomm.payment.adapter.out.gateway;

import com.ecomm.payment.application.port.out.PaymentGatewayPort;
import com.ecomm.payment.domain.AuthorizationRequest;
import com.ecomm.payment.domain.GatewayAuthorization;
import com.ecomm.payment.domain.PaymentGatewayUnavailableException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Stands in for a real gateway, reading the payment method as a test token, each answer with a
 * fresh {@code mock-} reference: {@code tok_approve} authorizes; {@code tok_decline} and {@code
 * tok_insufficient_funds} decline, as {@code card_declined} and {@code insufficient_funds}; {@code
 * tok_gateway_error} fails to answer; any other token declines as {@code unknown_payment_method}.
 * Every void succeeds.
 *
 * <p>As a real gateway does, it answers a repeated idempotency key with its first answer and
 * reference, for the most recent {@value #REMEMBERED_KEYS} keys, until the service restarts. A
 * failure to answer isn't remembered, so a retry can succeed.
 */
class MockPaymentGatewayAdapter implements PaymentGatewayPort {

  private static final int REMEMBERED_KEYS = 10_000;

  private final Map<String, GatewayAuthorization> authorizations = remembered();
  private final Map<String, String> voids = remembered();

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
          case "tok_gateway_error" ->
              throw new PaymentGatewayUnavailableException("The mock gateway failed, as asked");
          default -> new GatewayAuthorization.Declined(reference, "unknown_payment_method");
        };
    var first = authorizations.putIfAbsent(idempotencyKey, answer);
    return first != null ? first : answer;
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
