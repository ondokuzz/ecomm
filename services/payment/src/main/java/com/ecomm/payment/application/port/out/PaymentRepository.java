package com.ecomm.payment.application.port.out;

import com.ecomm.payment.domain.Payment;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Payments with their Payment transactions, which are only ever added to. */
public interface PaymentRepository {

  /** Records a new Payment with its transactions. */
  void add(Payment payment);

  /** Records {@code payment}'s newest transaction, and the status it now has. */
  void recordLatestTransaction(Payment payment);

  Optional<Payment> find(UUID id);

  /**
   * The Payment, locked until the transaction ends, so that changes to it are made one at a time.
   */
  Optional<Payment> lockToChange(UUID id);

  /** Every Payment for the Order, newest authorization first. */
  List<Payment> findByOrder(String orderId);

  /**
   * Up to {@code limit} of the Payments that existed before Payment events and are still to be
   * published, locked until the transaction ends; none that another transaction holds.
   */
  List<Payment> lockAwaitingBackfillEvent(int limit);

  /** Marks these Payments as published, so the backfill never publishes them again. */
  void markBackfillPublished(List<UUID> ids);
}
