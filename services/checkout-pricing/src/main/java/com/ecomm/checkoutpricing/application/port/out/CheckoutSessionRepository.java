package com.ecomm.checkoutpricing.application.port.out;

import com.ecomm.checkoutpricing.domain.CheckoutSession;
import java.time.Duration;
import java.util.Optional;

/**
 * Where Checkout Sessions live, each for as long as it is told to. A Customer has at most one:
 * saving a session makes it theirs, in place of any other.
 */
public interface CheckoutSessionRepository {

  /** Keeps {@code session} for {@code timeToLive}, as its Customer's one session. */
  void save(CheckoutSession session, Duration timeToLive);

  Optional<CheckoutSession> find(String sessionId);

  /** The Customer's session, if they have one that is still kept; it may have expired. */
  Optional<CheckoutSession> findByCustomer(String customerId);

  /** Removes {@code session}, and its Customer's pointer to it unless it already names another. */
  void delete(CheckoutSession session);
}
