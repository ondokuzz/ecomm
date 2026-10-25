package com.ecomm.cart.application.port.out;

import java.util.Optional;
import java.util.function.Supplier;

/** One abandonment sweep at a time, across every Cart instance. */
public interface SweepLock {

  /** Runs {@code sweep} holding the lock; empty, without running it, if another sweep holds it. */
  <T> Optional<T> whileHeld(Supplier<T> sweep);
}
