package com.ecomm.orchestration.application.port.out;

import io.temporal.activity.ActivityInterface;

/**
 * Order Management's {@code DELIVERED → RETURNED}, under an {@code Idempotency-Key} from the run,
 * recorded in the Order Status history as made by {@code ORCHESTRATION}.
 */
@ActivityInterface
public interface OrderReturnActivities {

  void markOrderReturned(String customerId, String orderId);
}
