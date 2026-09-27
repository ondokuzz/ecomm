package com.ecomm.ordermanagement.domain;

/** An Order was asked to move to an Order Status its current one can't reach. */
public class IllegalStatusTransitionException extends RuntimeException {

  public IllegalStatusTransitionException(OrderStatus from, OrderStatus to) {
    super("an order can't go from " + from + " to " + to);
  }
}
