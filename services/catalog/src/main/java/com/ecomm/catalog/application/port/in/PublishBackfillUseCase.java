package com.ecomm.catalog.application.port.in;

public interface PublishBackfillUseCase {

  /**
   * Publishes each Category and then each Product stored before Catalog events, once, with change
   * {@code BACKFILLED}, so consumers start complete; returns how many it published. Each is
   * published in the transaction that gives it its first version, so running it again, or on
   * another instance, publishes nothing more.
   */
  int publishBackfill();
}
