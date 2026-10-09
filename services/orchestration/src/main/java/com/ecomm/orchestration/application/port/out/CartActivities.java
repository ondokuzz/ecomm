package com.ecomm.orchestration.application.port.out;

import io.temporal.activity.ActivityInterface;

/** Cart's internal command. Clearing an empty Cart changes nothing, so it takes no key. */
@ActivityInterface
public interface CartActivities {

  void clearCart(String customerId);
}
