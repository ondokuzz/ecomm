package com.ecomm.notifications;

import static com.ecomm.notifications.Events.replacement;
import static com.ecomm.notifications.Events.shipment;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * An Order's Shipment shipped, then delivered, and a replacement shipped, each email their
 * Customer.
 */
class ShipmentMessagesApiTest extends NotificationsApiTest {

  NotificationsClient notifications;

  @BeforeEach
  void setUp() {
    notifications = new NotificationsClient(http);
  }

  @Test
  void anOrderShippedIsEmailedWithItsCarrierAndTrackingNumber() {
    var customer = newCustomerId();
    var address = newAddress("ada");
    Keycloak.customer(customer, address, "Ada", "Lovelace");
    var orderId = UUID.randomUUID().toString();
    var reference = "#" + orderId.substring(0, 8).toUpperCase();

    var shipment = shipment(orderId, customer).created().changedTo("PICKED").changedTo("PACKED");

    shipment.shipped("Mock Carrier", "MC0012345678");

    var sent = email.awaitOneSentTo(address);
    assertThat(sent.message().subject()).isEqualTo("Your Order " + reference + " is on its way");
    assertThat(sent.message().text())
        .contains("Hello Ada,")
        .contains("2 × Pixel 9")
        .contains("Mock Carrier")
        .contains("MC0012345678");
    assertThat(sent.message().html()).contains("MC0012345678");
    var notification = notifications.awaitSettled(STAFF, orderId);
    assertThat(notification.messageType()).isEqualTo("ORDER_SHIPPED");
    assertThat(notification.channel()).isEqualTo("EMAIL");
  }

  @Test
  void anOrderDeliveredIsEmailedWithTheDay() {
    var customer = newCustomerId();
    var address = newAddress("grace");
    Keycloak.customer(customer, address, "Grace", "Hopper");
    var orderId = UUID.randomUUID().toString();
    var shipment = shipment(orderId, customer).created().shipped("Mock Carrier", "MC1");
    email.awaitOneSentTo(address);

    shipment.delivered();

    var notifications =
        this.notifications.awaitForOrder(
            STAFF,
            orderId,
            l -> l.size() == 2 && l.stream().allMatch(n -> n.status().equals("SENT")));
    var delivered = notifications.getFirst();
    assertThat(delivered.messageType()).isEqualTo("ORDER_DELIVERED");
    assertThat(delivered.message().subject())
        .isEqualTo("Your Order #" + orderId.substring(0, 8).toUpperCase() + " was delivered");
    assertThat(delivered.message().text()).contains("delivered on October 23, 2026");
  }

  @Test
  void aReplacementShippedIsEmailedNamingItsRma() {
    var customer = newCustomerId();
    var address = newAddress("alan");
    Keycloak.customer(customer, address, "Alan", "Turing");
    var orderId = UUID.randomUUID().toString();
    var rmaId = UUID.randomUUID().toString();
    var rmaReference = "RMA-" + rmaId.substring(0, 8).toUpperCase();

    replacement(orderId, rmaId, customer).created().shipped("Mock Carrier", "MC0087654321");

    var sent = email.awaitOneSentTo(address);
    assertThat(sent.message().subject())
        .isEqualTo("Your replacement for " + rmaReference + " is on its way");
    assertThat(sent.message().text()).contains("MC0087654321");
    var notification = notifications.awaitSettled(STAFF, orderId);
    assertThat(notification.messageType()).isEqualTo("REPLACEMENT_SHIPPED");
    assertThat(notification.rmaId()).isEqualTo(rmaId);
    assertThat(notifications.staffList(STAFF, "rmaId=" + rmaId).items()).hasSize(1);
  }

  @Test
  void otherChangesAndARedeliveredShippedEventSendNothingMore() {
    var customer = newCustomerId();
    var address = newAddress("linus");
    Keycloak.customer(customer, address, "Linus", "T");
    var orderId = UUID.randomUUID().toString();
    var shipment =
        shipment(orderId, customer)
            .created()
            .changedTo("PICKED")
            .changedTo("PACKED")
            .changedTo("ON_HOLD")
            .shipped("Mock Carrier", "MC2");
    email.awaitOneSentTo(address);

    shipment.redelivered();
    var replacement =
        replacement(orderId, UUID.randomUUID().toString(), customer)
            .created()
            .shipped("Mock Carrier", "MC3");
    replacement.delivered();

    // The replacement's shipped email is sent after the redelivery and the replacement's delivery
    // are consumed: each topic is one partition, handled in order.
    await().atMost(java.time.Duration.ofSeconds(20)).until(() -> email.sentTo(address).size() >= 2);
    awaitShipmentsConsumed();
    assertThat(email.sentTo(address))
        .extracting(s -> s.message().subject())
        .containsExactly(
            "Your Order #" + orderId.substring(0, 8).toUpperCase() + " is on its way",
            "Your replacement for RMA-"
                + replacement.rmaId().substring(0, 8).toUpperCase()
                + " is on its way");
  }

  /**
   * Waits until every Shipment event sent so far is consumed: a marker Shipment's email sent means
   * every earlier one on the topic was handled.
   */
  private void awaitShipmentsConsumed() {
    var marker = newCustomerId();
    var address = "marker-" + marker + "@example.com";
    Keycloak.customer(marker, address, "Marker", "Customer");
    shipment(UUID.randomUUID().toString(), marker).created().shipped("Mock Carrier", "MC-MARK");
    email.awaitOneSentTo(address);
  }
}
