package com.ecomm.checkoutpricing.application.port.out;

import com.ecomm.checkoutpricing.application.port.in.CheckoutUnavailableException;
import com.ecomm.checkoutpricing.domain.CheckoutSession;
import com.ecomm.checkoutpricing.domain.PaymentAttempt;
import java.util.Optional;

/**
 * The checkout Saga, which Orchestration runs (ADR 0009): it places the Order, authorizes its
 * Payment, commits the Reservation, marks the Order paid, clears the Cart and ends the session. One
 * runs per attempt to pay a Checkout Session, named by the session's ID.
 */
public interface CheckoutSaga {

  /**
   * Starts an attempt to pay {@code session} with {@code paymentMethod}, or joins the one still
   * running for it, and waits a while for how it ends.
   *
   * @return the attempt, {@code PROCESSING} when it hasn't ended in that while
   * @throws CheckoutUnavailableException when the Saga can't be started; nothing is
   */
  PaymentAttempt pay(CheckoutSession session, String paymentMethod);

  /**
   * The latest attempt to pay the session, as it stands now, for as long as the Saga's history is
   * kept; empty when it was never paid.
   *
   * @throws CheckoutUnavailableException when the Saga can't be read
   */
  Optional<PaymentAttempt> latestAttempt(String sessionId);
}
