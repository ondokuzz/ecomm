package com.ecomm.ordermanagement.application.port.in;

public interface PublishBackfillUseCase {

  /**
   * Publishes each Order that existed before Order events, once, with change {@code BACKFILLED}, so
   * consumers start complete; returns how many it published. An Order is published in the same
   * transaction that marks it done, so running it again, or on another instance, publishes nothing
   * more.
   */
  int publishBackfill();
}
