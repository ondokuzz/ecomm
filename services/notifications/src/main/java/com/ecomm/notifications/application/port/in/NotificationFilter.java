package com.ecomm.notifications.application.port.in;

/** Which Notifications to list: those about an Order, an RMA, or to a Customer. */
public record NotificationFilter(Field field, String value) {

  public enum Field {
    ORDER,
    RMA,
    CUSTOMER
  }

  public static NotificationFilter ofCustomer(String customerId) {
    return new NotificationFilter(Field.CUSTOMER, customerId);
  }
}
