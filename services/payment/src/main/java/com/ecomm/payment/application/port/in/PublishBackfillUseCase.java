package com.ecomm.payment.application.port.in;

public interface PublishBackfillUseCase {

  /**
   * Publishes each Payment that existed before Payment events, once, with change {@code
   * BACKFILLED}, so consumers start complete; returns how many it published. A Payment is published
   * in the same transaction that marks it done, so running it again, or on another instance,
   * publishes nothing more.
   */
  int publishBackfill();
}
