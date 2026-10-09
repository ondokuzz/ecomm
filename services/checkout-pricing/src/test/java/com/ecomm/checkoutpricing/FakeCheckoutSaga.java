package com.ecomm.checkoutpricing;

import com.ecomm.checkoutpricing.application.port.in.CheckoutUnavailableException;
import com.ecomm.checkoutpricing.application.port.out.CheckoutSaga;
import com.ecomm.checkoutpricing.domain.CheckoutSession;
import com.ecomm.checkoutpricing.domain.PaymentAttempt;
import com.ecomm.checkoutpricing.domain.PaymentAttempt.Status;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stands in for the checkout Saga on Temporal, as the real adapter behaves: paying a session whose
 * attempt is still running joins it, and paying one whose attempt is over starts another. Each
 * attempt it starts ends as a test told it to, {@code PAID} unless told otherwise, or keeps running
 * until the test finishes it.
 */
class FakeCheckoutSaga implements CheckoutSaga {

  /** An attempt the Saga was started for: the session as it stood, and the Payment method. */
  record Started(CheckoutSession session, String paymentMethod) {}

  private final List<Started> started = new ArrayList<>();
  private final Map<String, PaymentAttempt> attempts = new ConcurrentHashMap<>();
  private volatile Status nextStatus;
  private volatile String nextDeclineReason;
  private volatile boolean unreachable;

  FakeCheckoutSaga() {
    reset();
  }

  void reset() {
    started.clear();
    attempts.clear();
    nextStatus = Status.PAID;
    nextDeclineReason = null;
    unreachable = false;
  }

  /** The next attempt started ends in {@code status}; {@code PROCESSING} keeps it running. */
  void answer(Status status) {
    answer(status, null);
  }

  void answer(Status status, String declineReason) {
    nextStatus = status;
    nextDeclineReason = declineReason;
  }

  /** Temporal can't be reached, so nothing can be started or read. */
  void unreachable() {
    unreachable = true;
  }

  /** The session's running attempt ends in {@code status}. */
  void finish(String sessionId, Status status) {
    attempts.computeIfPresent(
        sessionId,
        (id, a) -> new PaymentAttempt(a.customerId(), status, a.orderId(), a.declineReason()));
  }

  List<Started> started() {
    return List.copyOf(started);
  }

  @Override
  public synchronized PaymentAttempt pay(CheckoutSession session, String paymentMethod) {
    if (unreachable) {
      throw new CheckoutUnavailableException(new RuntimeException("UNAVAILABLE"));
    }
    var running = attempts.get(session.id());
    if (running != null && running.status() == Status.PROCESSING) {
      return running;
    }
    started.add(new Started(session, paymentMethod));
    var attempt =
        new PaymentAttempt(
            session.customerId(), nextStatus, CheckoutApiTest.ORDER_ID, nextDeclineReason);
    attempts.put(session.id(), attempt);
    return attempt;
  }

  @Override
  public Optional<PaymentAttempt> latestAttempt(String sessionId) {
    if (unreachable) {
      throw new CheckoutUnavailableException(new RuntimeException("UNAVAILABLE"));
    }
    return Optional.ofNullable(attempts.get(sessionId));
  }
}
