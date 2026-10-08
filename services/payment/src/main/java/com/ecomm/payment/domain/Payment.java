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
   * The Payment the gateway's answer to {@code request} makes, at {@code at}: authorized or
   * declined.
   */
  public static Payment fromAuthorization(
      UUID id, AuthorizationRequest request, GatewayAuthorization answer, Instant at) {
    return new Payment(
        id,
        request.customerId(),
        request.orderId(),
        request.amount(),
        List.of(PaymentTransaction.authorization(request.amount(), answer, at)));
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

  public boolean belongsTo(String customerId) {
    return this.customerId.equals(customerId);
  }
}
