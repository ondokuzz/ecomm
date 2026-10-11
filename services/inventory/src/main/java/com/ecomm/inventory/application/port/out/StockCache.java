package com.ecomm.inventory.application.port.out;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;

/**
 * The Stock cache: one entry per Variant, kept to spare Postgres reads and doomed Reservations.
 * Postgres stays the only authority, and the cache is never required: when it can't be reached,
 * {@link #get} finds nothing and {@link #put} does nothing.
 */
public interface StockCache {

  /** The entries of those Variants still fresh at {@code now}, by Variant ID. */
  Map<String, CachedStock> get(Collection<String> variantIds, Instant now);

  /**
   * Writes each entry, to live until its {@code freshUntil} by {@code now}, unless the cache holds
   * a newer version of its Variant. So a write that lands late never overwrites a newer one.
   */
  void put(Collection<CachedStock> entries, Instant now);
}
