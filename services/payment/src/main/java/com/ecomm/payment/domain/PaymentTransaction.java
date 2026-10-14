package com.ecomm.payment.domain;

import com.ecomm.commons.money.Money;
import java.time.Instant;
import java.util.Objects;

/**
 * One interaction with the payment gateway on a Payment, recorded for good and never changed (ADR
 * 0002): an authorization, a void or a capture, for an amount, with the gateway's answer and its
 * reference for it.
 *
 * @param declineReason the gateway's reason, such as {@code insufficient_funds}, when the outcome
 *     is {@code DECLINED}; null otherwise
 * @param gatewayEventId the gateway's event ID when a webhook recorded it; null otherwise
 * @param backfilled true for an authorization reconstructed from a Payment recorded before the
 *     ledger, whose time is when it was reconstructed, not when it happened
 */
public record PaymentTransaction(
    Kind kind,
    Money amount,
    Outcome outcome,
    String gatewayReference,
    String declineReason,
    String gatewayEventId,
    Instant at,
    boolean backfilled) {

  /** What the gateway was asked to do. Refund comes with returns. */
  public enum Kind {
    AUTHORIZATION,
    VOID,
    CAPTURE
  }

  /** What the gateway answered: {@code PENDING} until it settles the answer later. */
  public enum Outcome {
    APPROVED,
    DECLINED,
    PENDING
  }

  public PaymentTransaction {
    Objects.requireNonNull(kind, "kind");
    Objects.requireNonNull(amount, "amount");
    Objects.requireNonNull(outcome, "outcome");
    Objects.requireNonNull(gatewayReference, "gatewayReference");
    Objects.requireNonNull(at, "at");
  }

  /**
   * The authorization the gateway's {@code answer} makes, for {@code amount}, at {@code at}; {@code
   * gatewayEventId} names the Gateway webhook that brought the answer, or is null when the gateway
   * answered the request itself.
   */
  static PaymentTransaction authorization(
      Money amount, GatewayAuthorization answer, String gatewayEventId, Instant at) {
    return new PaymentTransaction(
        Kind.AUTHORIZATION,
        amount,
        answer.outcome(),
        answer.reference(),
        answer.declineReason(),
        gatewayEventId,
        at,
        false);
  }

  /** A void of {@code amount} the gateway confirmed with {@code reference}. */
  static PaymentTransaction voidOf(Money amount, String reference, Instant at) {
    return new PaymentTransaction(
        Kind.VOID, amount, Outcome.APPROVED, reference, null, null, at, false);
  }

  /** A capture of {@code amount} the gateway approved with {@code reference}. */
  static PaymentTransaction captureOf(Money amount, String reference, Instant at) {
    return new PaymentTransaction(
        Kind.CAPTURE, amount, Outcome.APPROVED, reference, null, null, at, false);
  }

  /**
   * A capture of {@code amount} the gateway declined, with {@code reference}, for {@code reason}.
   */
  static PaymentTransaction declinedCaptureOf(
      Money amount, String reference, String reason, Instant at) {
    return new PaymentTransaction(
        Kind.CAPTURE, amount, Outcome.DECLINED, reference, reason, null, at, false);
  }
}
