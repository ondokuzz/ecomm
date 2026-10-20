package com.ecomm.returnswarranty.application.port.out;

import com.ecomm.returnswarranty.domain.Order;
import java.util.Optional;

/** The Order projection. */
public interface OrderStore {

  /** The version of the Order held for {@code id}, if any. */
  Optional<Long> versionOf(String id);

  /** Keeps the Order, replacing what was held for it. */
  void save(Order order);

  Optional<Order> find(String id);
}
