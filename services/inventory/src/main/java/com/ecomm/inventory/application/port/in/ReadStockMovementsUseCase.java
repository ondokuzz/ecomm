package com.ecomm.inventory.application.port.in;

import com.ecomm.inventory.domain.StockMovement;
import java.util.Optional;

public interface ReadStockMovementsUseCase {

  /**
   * A Variant's On-hand and the sum of its On-hand movements, read together, with one page of its
   * movements, newest first.
   *
   * @param onHandFromMovements what On-hand should be, from the ledger alone
   */
  record StockLedger(
      String variantId, int onHand, long onHandFromMovements, Page<StockMovement> movements) {

    /** Whether the Stock counter agrees with its ledger. */
    public boolean balanced() {
      return onHand == onHandFromMovements;
    }
  }

  /**
   * Empty when Inventory doesn't stock the Variant. {@code page} counts from 0, of {@code size}
   * movements.
   */
  Optional<StockLedger> movements(String variantId, int page, int size);
}
