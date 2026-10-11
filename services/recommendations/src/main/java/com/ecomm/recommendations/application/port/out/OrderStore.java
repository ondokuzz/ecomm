package com.ecomm.recommendations.application.port.out;

import com.ecomm.recommendations.domain.Order;
import java.util.Optional;

public interface OrderStore {

  /** The version held of the Order, if any. */
  Optional<Long> versionOf(String orderId);

  /** Replaces whatever is held of the Order. */
  void save(Order order);
}
