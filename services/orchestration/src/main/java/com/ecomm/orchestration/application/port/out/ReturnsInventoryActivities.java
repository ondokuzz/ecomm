package com.ecomm.orchestration.application.port.out;

import io.temporal.activity.ActivityInterface;

/**
 * Inventory's restock, under an {@code Idempotency-Key} from the run, and the Reservation of an
 * RMA's replacement, owned by {@code rma:<rmaId>}. Reserving again for the same owner answers the
 * Reservation it has, and committing again changes nothing, so neither takes a key.
 */
@ActivityInterface
public interface ReturnsInventoryActivities {

  /** Puts the returned units back on hand, naming the RMA reference. */
  void restock(String variantId, int quantity, String reference);

  /**
   * Holds {@code quantity} of the Variant for the RMA's replacement: the Reservation's ID, or null
   * when there is too little Stock ({@code insufficientStock}).
   */
  String reserveReplacement(String rmaId, String variantId, int quantity);

  /**
   * Takes the replacement's Reservation off on-hand for good: true, or false when it no longer held
   * the Stock (it expired before it was committed), so it is reserved again.
   */
  boolean commitReplacement(String rmaId, String reservationId);
}
