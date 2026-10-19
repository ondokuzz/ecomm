package com.ecomm.payment.domain;

import com.ecomm.commons.money.Money;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * An Order's amount for one Customer, through the gateway, with its Payment transactions oldest
 * first. Its status, decline reason and gateway reference are worked out from them, never set. It
 * belongs to the Customer it was authorized for, and only they and Staff may see it.
 *
 * @throws IllegalStateException when the transactions are a sequence no Payment can have
 */
public record Payment(
    UUID id,
    String customerId,
    String orderId,
    Money amount,
    List<PaymentTransaction> transactions) {

  public Payment {
    transactions = List.copyOf(transactions);
    PaymentStatus.of(transactions);
  }

  /**
   * The Payment the gateway's answer to {@code request} makes, at {@code at}: authorized, declined
   * or pending.
   */
  public static Payment fromAuthorization(
      UUID id, AuthorizationRequest request, GatewayAuthorization answer, Instant at) {
    return new Payment(
        id,
        request.customerId(),
        request.orderId(),
        request.amount(),
        List.of(PaymentTransaction.authorization(request.amount(), answer, null, at)));
  }

  public PaymentStatus status() {
    return PaymentStatus.of(transactions);
  }

  /** The gateway's reason for the decline when the status is {@code DECLINED}; null otherwise. */
  public String declineReason() {
    return status() == PaymentStatus.DECLINED ? transactions.getLast().declineReason() : null;
  }

  /** The gateway's reference for the authorization. */
  public String gatewayReference() {
    return transactions.getFirst().gatewayReference();
  }

  /** Goes up by one with every transaction. */
  public long version() {
    return transactions.size();
  }

  /**
   * This Payment with its pending authorization settled by the gateway's {@code settlement}, at
   * {@code at}: authorized or declined.
   *
   * @throws IllegalStateException unless it is {@code PENDING}
   */
  public Payment settled(GatewaySettlement settlement, Instant at) {
    if (status() != PaymentStatus.PENDING) {
      throw new IllegalStateException("Payment " + id + " is " + status() + ", not PENDING");
    }
    var appended = new ArrayList<>(transactions);
    appended.add(
        PaymentTransaction.authorization(amount, settlement.answer(), settlement.eventId(), at));
    return new Payment(id, customerId, orderId, amount, appended);
  }

  /** Whether its authorization can still be released: it is authorized or pending. */
  public boolean isVoidable() {
    var status = status();
    return status == PaymentStatus.AUTHORIZED || status == PaymentStatus.PENDING;
  }

  /**
   * This Payment with a {@code VOID} transaction the gateway confirmed with {@code reference}.
   *
   * @throws PaymentNotVoidableException unless it {@link #isVoidable()}
   */
  public Payment voided(String reference, Instant at) {
    if (!isVoidable()) {
      throw new PaymentNotVoidableException(id, status());
    }
    var appended = new ArrayList<>(transactions);
    appended.add(PaymentTransaction.voidOf(amount, reference, at));
    return new Payment(id, customerId, orderId, amount, appended);
  }

  /**
   * This Payment with a {@code CAPTURE} transaction the gateway approved with {@code reference}:
   * {@code CAPTURED}.
   *
   * @throws PaymentNotCapturableException unless it is {@code AUTHORIZED}
   */
  public Payment captured(String reference, Instant at) {
    return withCapture(PaymentTransaction.captureOf(amount, reference, at));
  }

  /**
   * This Payment with a {@code CAPTURE} transaction the gateway declined with {@code reference},
   * for {@code reason}: still {@code AUTHORIZED}.
   *
   * @throws PaymentNotCapturableException unless it is {@code AUTHORIZED}
   */
  public Payment captureDeclined(String reference, String reason, Instant at) {
    return withCapture(PaymentTransaction.declinedCaptureOf(amount, reference, reason, at));
  }

  private Payment withCapture(PaymentTransaction capture) {
    if (status() != PaymentStatus.AUTHORIZED) {
      throw new PaymentNotCapturableException(id, status());
    }
    var appended = new ArrayList<>(transactions);
    appended.add(capture);
    return new Payment(id, customerId, orderId, amount, appended);
  }

  /**
   * What is left to refund of the amount captured; null unless it is captured or partly refunded.
   */
  public Money refundable() {
    return PaymentStatus.Ledger.of(transactions).refundable();
  }

  /**
   * This Payment with a {@code REFUND} of {@code refund}, for {@code refundFor}, the gateway
   * approved with {@code reference}: {@code PARTIALLY_REFUNDED}, or {@code REFUNDED} once nothing
   * is left.
   *
   * @throws PaymentNotRefundableException unless it is captured or partly refunded
   * @throws RefundExceedsCapturedException when {@code refund} is more than {@link #refundable()},
   *     or in another Currency
   */
  public Payment refunded(Money refund, String reference, String refundFor, Instant at) {
    return withRefund(PaymentTransaction.refundOf(refund, reference, refundFor, at));
  }

  /**
   * This Payment with a {@code REFUND} of {@code refund}, for {@code refundFor}, the gateway
   * declined with {@code reference} for {@code reason}: its status unchanged.
   *
   * @throws PaymentNotRefundableException unless it is captured or partly refunded
   * @throws RefundExceedsCapturedException as for {@link #refunded}
   */
  public Payment refundDeclined(
      Money refund, String reference, String reason, String refundFor, Instant at) {
    return withRefund(
        PaymentTransaction.declinedRefundOf(refund, reference, reason, refundFor, at));
  }

  /**
   * Checks a Refund of {@code refund} could be made: refused, with nothing asked of the gateway,
   * when it couldn't.
   *
   * @throws PaymentNotRefundableException unless it is captured or partly refunded
   * @throws RefundExceedsCapturedException when {@code refund} is more than {@link #refundable()},
   *     or in another Currency
   */
  public void checkRefundable(Money refund) {
    var left = refundable();
    if (left == null) {
      throw new PaymentNotRefundableException(id, status());
    }
    if (!refund.currency().equals(left.currency()) || refund.amountMinor() > left.amountMinor()) {
      throw new RefundExceedsCapturedException(id, refund, left);
    }
  }

  private Payment withRefund(PaymentTransaction refund) {
    checkRefundable(refund.amount());
    var appended = new ArrayList<>(transactions);
    appended.add(refund);
    return new Payment(id, customerId, orderId, amount, appended);
  }

  public boolean belongsTo(String customerId) {
    return this.customerId.equals(customerId);
  }
}
