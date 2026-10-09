package com.ecomm.payment.application.port.in;

import com.ecomm.payment.domain.GatewaySettlement;
import com.ecomm.payment.domain.Payment;
import java.util.Optional;

public interface SettleAuthorizationUseCase {

  /** What a Gateway webhook did to the Payment it names. */
  enum Outcome {
    /** It settled the pending authorization, approved or declined. */
    SETTLED,
    /** Its event ID was received before, so it changed nothing. */
    ALREADY_RECEIVED,
    /** The Payment was already settled or voided: it is recorded as received, nothing more. */
    NOT_PENDING
  }

  /** The Payment as the webhook left it, and what the webhook did. */
  record Settlement(Payment payment, Outcome outcome) {}

  /**
   * Records the Gateway webhook {@code settlement} at most once per event ID, and settles the
   * Payment whose authorization has its reference, if that is still pending, publishing the
   * Payment. Empty when no Payment's authorization has that reference; nothing is recorded then.
   */
  Optional<Settlement> settle(GatewaySettlement settlement);
}
