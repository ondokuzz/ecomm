package com.ecomm.notifications;

import static com.ecomm.notifications.Events.order;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** An Order's change to {@code PAID} sends its Customer an {@code ORDER_CONFIRMED} email. */
class OrderConfirmedApiTest extends NotificationsApiTest {

  @Test
  void aPaidOrderIsConfirmedByEmailListingItsLinesAndTotal() {
    var customer = newCustomerId();
    var address = newAddress("ada");
    Keycloak.customer(customer, address, "Ada", "Lovelace");
    var variant = projectedVariant("Google Pixel 9", "storage", "128 GB");
    var order = order(customer).line(variant, 2, 79900).placed();

    order.paid();

    var sent = email.awaitOneSentTo(address);
    assertThat(sent.recipient().givenName()).isEqualTo("Ada");
    assertThat(sent.message().subject())
        .isEqualTo("Your Order " + order.reference() + " is confirmed");
    assertThat(sent.message().text())
        .contains("Hello Ada,")
        .contains("2 × Google Pixel 9 (128 GB)")
        .contains("€1,598.00")
        .contains("Shipping: €4.99")
        .contains("Total: €1,602.99");
    assertThat(sent.message().html()).contains("Google Pixel 9 (128 GB)").contains("€1,602.99");
  }
}
