package com.ecomm.inventory.application.port.out;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * The on-hand count of each Variant Inventory stocks, with the version its Stock events carry. What
 * Reservations hold is worked out apart. Every write gives the Variant a new version, higher than
 * any it has had, even before it was last removed.
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
   * Adds a Variant with this on-hand count, returning its version; empty, changing nothing, when it
   * already exists.
   */
  OptionalLong insertIfAbsent(String variantId, int onHand);

  /**
   * Writes the on-hand counts of existing Variants, changed or not, and returns each one's new
   * version.
   */
  Map<String, Long> update(Map<String, Integer> onHandByVariant);

  /** Drops the Variant's row, returning the version its removal is published with. */
  long delete(String variantId);

  /**
   * Up to {@code limit} Variants stocked before Stock events whose backfill event is still to be
   * published, locked so no other instance takes them; Variants no longer stocked included.
   */
  List<String> lockAwaitingBackfillEvent(int limit);

  void markBackfillPublished(Collection<String> variantIds);
}
