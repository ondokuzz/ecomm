package com.ecomm.cart.application;

import com.ecomm.cart.application.port.in.DetectAbandonedCartsUseCase;
import com.ecomm.cart.application.port.out.AbandonedCartCounter;
import com.ecomm.cart.application.port.out.AbandonedCartPublisher;
import com.ecomm.cart.application.port.out.CartRepository;
import com.ecomm.cart.application.port.out.SweepLock;
import com.ecomm.cart.application.port.out.TimeSource;
import java.time.Duration;

/**
 * Finds Carts idle past {@code abandonedAfter}, publishes each, then marks it. A Cart is marked
 * only once its event is acknowledged, so a sweep that fails or stops in between leaves it for the
 * next sweep, which publishes the same event again.
 *
 * <p>A sweep starts no batch after {@link #BUDGET}, well inside the lock's hold, and leaves the
 * rest to the next, so two sweeps don't overlap.
 */
public class AbandonmentService implements DetectAbandonedCartsUseCase {

  private static final int BATCH = 100;
  private static final Duration BUDGET = Duration.ofMinutes(2);

  private final CartRepository carts;
  private final AbandonedCartPublisher events;
  private final AbandonedCartCounter counter;
  private final SweepLock lock;
  private final TimeSource time;
  private final Duration abandonedAfter;

  public AbandonmentService(
      CartRepository carts,
      AbandonedCartPublisher events,
      AbandonedCartCounter counter,
      SweepLock lock,
      TimeSource time,
      Duration abandonedAfter) {
    this.carts = carts;
    this.events = events;
    this.counter = counter;
    this.lock = lock;
    this.time = time;
    this.abandonedAfter = abandonedAfter;
  }

  @Override
  public int sweep() {
    return lock.whileHeld(this::sweepHoldingTheLock).orElse(0);
  }

  private int sweepHoldingTheLock() {
    var started = time.now();
    var cutoff = started.minus(abandonedAfter);
    var marked = 0;
    while (time.now().isBefore(started.plus(BUDGET))) {
      var idle = carts.idleSince(cutoff, BATCH);
      for (var cart : idle) {
        events.publish(cart);
        // A Cart changed since it was read stays a candidate, under its new lastChangedAt.
        if (carts.markAbandoned(cart)) {
          counter.increment();
          marked++;
        }
      }
      if (idle.isEmpty()) {
        break;
      }
    }
    return marked;
  }
}
