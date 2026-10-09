package com.ecomm.orchestration.application.port.out;

import io.temporal.activity.ActivityInterface;

/**
 * Checkout's internal command. Ending a session already gone, or one the Customer has replaced,
 * changes nothing, so it takes no key.
 */
@ActivityInterface
public interface CheckoutSessionActivities {

  void endCheckoutSession(String customerId, String checkoutSessionId);
}
