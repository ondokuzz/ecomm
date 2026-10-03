package com.ecomm.commons.events;

import java.util.Optional;
import java.util.function.ToLongFunction;

/**
 * How a consumer applies events that may arrive twice or out of order: it keeps the newest version
 * it has seen of each aggregate and ignores any event whose version is not newer (ADR 0002).
 */
public final class Versions {

  private Versions() {}

  /**
   * Runs {@code apply} if {@code incomingVersion} is newer than the stored copy's, or if nothing is
   * stored yet; otherwise the event is a duplicate or stale, and nothing happens.
   *
   * <p>Read {@code stored} and run {@code apply} in one transaction. One aggregate's events arrive
   * on one partition, so a consumer group handles them one at a time.
   *
   * @return whether {@code apply} ran
   */
  public static <T> boolean applyIfNewer(
      long incomingVersion, Optional<T> stored, ToLongFunction<T> versionOf, Runnable apply) {
    if (stored.isPresent() && incomingVersion <= versionOf.applyAsLong(stored.get())) {
      return false;
    }
    apply.run();
    return true;
  }
}
