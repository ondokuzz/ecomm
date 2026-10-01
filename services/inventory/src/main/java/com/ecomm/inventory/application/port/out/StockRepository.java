package com.ecomm.inventory.application.port.out;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/**
 * The on-hand count of each Variant Inventory stocks. What Reservations hold is worked out apart.
 */
public interface StockRepository {

  Optional<Integer> onHand(String variantId);

  /**
   * The on-hand counts of those Variants that exist, locked until the surrounding transaction ends,
   * so no concurrent change can touch them in between. Always locks in Variant ID order, so two
   * overlapping batches cannot deadlock.
   */
  Map<String, Integer> lockOnHand(Collection<String> variantIds);

  /**
   * Adds a Variant with this on-hand count; {@code false}, changing nothing, when it already
   * exists.
   */
  boolean insertIfAbsent(String variantId, int onHand);

  /** Overwrites the on-hand counts of existing Variants. */
  void setOnHand(Map<String, Integer> onHandByVariant);

  /** Drops the Variant's row. */
  void delete(String variantId);
}
