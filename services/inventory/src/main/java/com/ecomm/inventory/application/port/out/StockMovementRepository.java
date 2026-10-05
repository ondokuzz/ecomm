package com.ecomm.inventory.application.port.out;

import com.ecomm.inventory.domain.StockMovement;
import java.util.Collection;
import java.util.List;

/** The ledger: Stock movements, only ever appended. */
public interface StockMovementRepository {

  /** Records them after every movement recorded before, in this order. */
  void append(Collection<StockMovement> movements);

  /** The Variant's movements, newest first, skipping {@code offset} of them. */
  List<StockMovement> newestFirst(String variantId, long offset, int limit);

  long count(String variantId);

  /** The sum of the Variant's On-hand changes: what its On-hand should be. */
  long onHandSum(String variantId);
}
