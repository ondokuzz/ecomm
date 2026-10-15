package com.ecomm.returnswarranty.application.port.in;

import com.ecomm.returnswarranty.domain.RmaStatus;

/**
 * Which RMAs to list; a null field doesn't narrow it.
 *
 * @param customerId only this Customer's, as a Customer lists them
 */
public record RmaFilter(String customerId, RmaStatus status, String orderId) {}
