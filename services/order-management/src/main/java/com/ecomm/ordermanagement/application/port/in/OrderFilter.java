package com.ecomm.ordermanagement.application.port.in;

import com.ecomm.ordermanagement.domain.OrderStatus;
import java.time.Instant;

/**
 * Which Orders Staff want to see: those of one Customer, in one Order Status, placed from {@code
 * placedFrom} up to, but not including, {@code placedTo}, whose ID starts with {@code idPrefix}. A
 * filter left null matches every Order.
 *
 * @param idPrefix the start of an Order's ID in lower case, as an Order reference is: hex digits
 *     and hyphens only
 */
public record OrderFilter(
    String customerId, OrderStatus status, Instant placedFrom, Instant placedTo, String idPrefix) {}
