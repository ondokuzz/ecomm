package com.ecomm.notifications;

import static com.ecomm.notifications.Events.payment;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * An approved Refund emails its Customer the amount and what it was for. A Payment authorized,
 * declined or captured, and a Refund the gateway refused, send nothing.
 */
class RefundMessagesApiTest extends NotificationsApiTest {

  NotificationsClient notifications;

  @BeforeEach
  void setUp() {
    notifications = new NotificationsClient(http);
  }

  @Test
  void aRefundIsEmailedWithItsAmountAndReference() {
    var customer = newCustomerId();
    var address = newAddress("ada");
    Keycloak.customer(customer, address, "Ada", "Lovelace");
    var orderId = UUID.randomUUID().toString();
    var reference = "#" + orderId.substring(0, 8).toUpperCase();
    var payment = payment(orderId, customer, 159800).authorized().captured();

    payment.refunded(79900, "RMA-7C4B2E1A");

    var sent = email.awaitOneSentTo(address);
    assertThat(sent.message().subject())
        .isEqualTo("We've refunded €799.00 for your Order " + reference);
    assertThat(sent.message().text()).contains("€799.00").contains("for RMA-7C4B2E1A");
    var notification = notifications.awaitSettled(STAFF, orderId);
    assertThat(notification.messageType()).isEqualTo("PAYMENT_REFUNDED");
  }

  @Test
  void eachRefundIsEmailedOnceAndNothingElseIs() {
    var customer = newCustomerId();
    var address = newAddress("grace");
    Keycloak.customer(customer, address, "Grace", "Hopper");
    var declinedCustomer = newCustomerId();
    var declinedAddress = newAddress("declined");
    Keycloak.customer(declinedCustomer, declinedAddress, "De", "Clined");
    payment(UUID.randomUUID().toString(), declinedCustomer, 2500).declined();
    var orderId = UUID.randomUUID().toString();
    var payment = payment(orderId, customer, 10000).authorized().captured();

    payment.refunded(3000, null).redelivered().refundDeclined(2000).refunded(7000, null);

    var settled =
        notifications.awaitForOrder(
            STAFF,
            orderId,
            l -> l.size() == 2 && l.stream().allMatch(n -> n.status().equals("SENT")));
    assertThat(settled)
        .extracting(n -> n.message().subject())
        .containsExactly(
            "We've refunded €70.00 for your Order #" + orderId.substring(0, 8).toUpperCase(),
            "We've refunded €30.00 for your Order #" + orderId.substring(0, 8).toUpperCase());
    assertThat(email.sentTo(address)).hasSize(2);
    assertThat(email.sentTo(declinedAddress)).isEmpty();
  }
}
