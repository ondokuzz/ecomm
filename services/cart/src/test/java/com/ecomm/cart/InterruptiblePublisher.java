package com.ecomm.cart;

import com.ecomm.cart.application.port.out.AbandonedCartPublisher;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.config.BeanPostProcessor;

/**
 * Wraps the real publisher, so a test can stop a sweep just after a Customer's event is
 * acknowledged, as a crash before the Cart is marked would, or hold a sweep just before it
 * publishes, while another sweep runs.
 */
class InterruptiblePublisher implements BeanPostProcessor {

  private final ConcurrentHashMap<String, Boolean> crashAfter = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, Hold> holds = new ConcurrentHashMap<>();

  /** A sweep held just before publishing a Customer's event, until {@link #release()}. */
  static final class Hold {
    private final CountDownLatch reached = new CountDownLatch(1);
    private final CountDownLatch released = new CountDownLatch(1);

    void awaitReached() throws InterruptedException {
      if (!reached.await(30, TimeUnit.SECONDS)) {
        throw new AssertionError("No sweep reached the hold");
      }
    }

    void release() {
      released.countDown();
    }
  }

  /** The next publish of the Customer's event is acknowledged, then the sweep stops. */
  void crashAfterPublishing(String customerId) {
    crashAfter.put(customerId, true);
  }

  /** The next publish of the Customer's event waits until the hold is released. */
  Hold holdBeforePublishing(String customerId) {
    var hold = new Hold();
    holds.put(customerId, hold);
    return hold;
  }

  @Override
  public Object postProcessAfterInitialization(Object bean, String beanName) {
    if (!(bean instanceof AbandonedCartPublisher real)) {
      return bean;
    }
    return (AbandonedCartPublisher)
        abandoned -> {
          var hold = holds.remove(abandoned.customerId());
          if (hold != null) {
            hold.reached.countDown();
            try {
              hold.released.await(30, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
            }
          }
          real.publish(abandoned);
          if (crashAfter.remove(abandoned.customerId()) != null) {
            throw new IllegalStateException("The sweep stopped before marking the Cart");
          }
        };
  }
}
