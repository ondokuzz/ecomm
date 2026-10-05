package com.ecomm.inventory.application.port.in;

public interface PublishBackfillUseCase {

  /**
   * Publishes the Stock of each Variant stocked before Stock events, once, with change {@code
   * BACKFILLED}, so consumers start complete; returns how many it published. A Variant is published
   * in the same transaction that marks it done, so running it again, or on another instance,
   * publishes nothing more.
   */
  int publishBackfill();
}
