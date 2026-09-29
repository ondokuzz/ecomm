package com.ecomm.inventory.application.port.in;

import com.ecomm.inventory.domain.Stock;

public interface SetOnHandUseCase {

  /** The Variant's Stock after the change, and whether this created it. */
  record Result(Stock stock, boolean created) {}

  /**
   * Sets how many units are on hand, adding the Variant if Inventory doesn't stock it yet. Throws
   * {@code OnHandBelowReservedException} when Reservations hold more than {@code onHand}.
   */
  Result setOnHand(String variantId, int onHand);
}
