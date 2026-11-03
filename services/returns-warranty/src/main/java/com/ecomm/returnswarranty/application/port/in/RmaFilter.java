package com.ecomm.returnswarranty.application.port.in;

import com.ecomm.returnswarranty.domain.RmaStatus;
import java.util.Set;

/**
 * Which RMAs to list; a null field doesn't narrow it.
 *
 * @param customerId only this Customer's, as a Customer lists them, newest first
 * @param statuses only those in one of these; empty for any
 * @param reference only those whose RMA reference this is: the first eight characters of the RMA's
 *     ID in lower case, as the database writes a UUID
 */
public record RmaFilter(
    String customerId, Set<RmaStatus> statuses, String orderId, String reference) {

  public RmaFilter {
    statuses = Set.copyOf(statuses);
  }
}
