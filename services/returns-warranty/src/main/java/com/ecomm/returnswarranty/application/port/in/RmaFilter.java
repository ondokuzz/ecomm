package com.ecomm.returnswarranty.application.port.in;

import com.ecomm.returnswarranty.domain.RmaStatus;
import java.util.Set;

/**
 * Which RMAs to list; a null field doesn't narrow it.
 *
 * @param customerId only this Customer's, as a Customer lists them, newest first
 * @param statuses only those in one of these; empty for any
 */
public record RmaFilter(String customerId, Set<RmaStatus> statuses, String orderId) {

  public RmaFilter {
    statuses = Set.copyOf(statuses);
  }
}
