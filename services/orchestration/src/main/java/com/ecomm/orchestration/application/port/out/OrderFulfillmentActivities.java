package com.ecomm.orchestration.application.port.out;

import io.temporal.activity.ActivityInterface;

/**
 * Order Management's Status changes along fulfillment, each under an {@code Idempotency-Key} from
 * the run, and recorded in the Order Status history as made by {@code ORCHESTRATION}.
 */
@ActivityInterface
public interface OrderFulfillmentActivities {

  void markOrderFulfilled(String customerId, String orderId);

  void markOrderShipped(String customerId, String orderId);

  void markOrderDelivered(String customerId, String orderId);
}
