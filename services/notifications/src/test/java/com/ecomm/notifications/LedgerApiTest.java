package com.ecomm.notifications;

import static com.ecomm.notifications.Events.order;
import static org.assertj.core.api.Assertions.assertThat;

import com.ecomm.commons.security.FakeKeycloak;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The Notification ledger records each Notification once per event, Message type and channel, and
 * Staff and its Customer read it, the address masked.
 */
class LedgerApiTest extends NotificationsApiTest {

  NotificationsClient notifications;

  @BeforeEach
  void setUp() {
    notifications = new NotificationsClient(http);
  }

  @Test
  void aSentNotificationIsInTheLedgerWithItsMessageAndAttempt() {
    var customer = newCustomerId();
    Keycloak.customer(customer, "grace@example.com", "Grace", "Hopper");

    var order = order(customer).line("VARIANT-1", 1, 2500).placed().paid();

    var notification = notifications.awaitSettled(STAFF, order.orderId());
    assertThat(notification.messageType()).isEqualTo("ORDER_CONFIRMED");
    assertThat(notification.channel()).isEqualTo("EMAIL");
    assertThat(notification.customerId()).isEqualTo(customer);
    assertThat(notification.status()).isEqualTo("SENT");
    assertThat(notification.recipient()).isEqualTo("g***@example.com");
    assertThat(notification.message().subject()).contains(order.reference());
    assertThat(notification.message().text()).contains("Hello Grace,");
    assertThat(notification.attempts()).hasSize(1);
    assertThat(notification.attempts().getFirst().outcome()).isEqualTo("SENT");
    assertThat(notification.sentAt()).isNotNull();
  }

  @Test
  void theSameEventDeliveredTwiceRecordsAndSendsOnce() {
    var customer = newCustomerId();
    var address = newAddress("linus");
    Keycloak.customer(customer, address, "Linus", "T");
    var order = order(customer).line("VARIANT-1", 1, 2500).placed().paid();
    notifications.awaitSettled(STAFF, order.orderId());

    order.redelivered();

    awaitEverythingConsumed();
    assertThat(notifications.staffList(STAFF, "orderId=" + order.orderId()).items()).hasSize(1);
    assertThat(email.sentTo(address)).hasSize(1);
  }

  @ParameterizedTest
  @ValueSource(strings = {"FULFILLED", "SHIPPED", "DELIVERED", "CANCELLED", "RETURNED"})
  void aChangeToAnythingButPaidRecordsNothing(String status) {
    var customer = newCustomerId();
    Keycloak.customer(customer, "nobody@example.com", "No", "Body");

    var order = order(customer).line("VARIANT-1", 1, 2500).placed().changedTo(status);

    awaitEverythingConsumed();
    assertThat(notifications.staffList(STAFF, "orderId=" + order.orderId()).items()).isEmpty();
  }

  @Test
  void staffReadByCustomerAndTheCustomerReadsTheirOwn() {
    var customer = newCustomerId();
    Keycloak.customer(customer, newAddress("ada"), "Ada", "Lovelace");
    var first = order(customer).line("VARIANT-1", 1, 2500).placed().paid();
    var second = order(customer).line("VARIANT-2", 1, 2500).placed().paid();
    notifications.awaitSettled(STAFF, first.orderId());
    notifications.awaitSettled(STAFF, second.orderId());

    var staffView = notifications.staffList(STAFF, "customerId=" + customer);
    var own = notifications.mine(FakeKeycloak.token(customer, "CUSTOMER"));

    assertThat(staffView.items())
        .extracting(NotificationsClient.NotificationView::orderId)
        .containsExactly(second.orderId(), first.orderId());
    assertThat(own.items()).isEqualTo(staffView.items());
    assertThat(own.items())
        .allSatisfy(n -> assertThat(n.recipient()).isEqualTo("a***@example.com"));
    assertThat(notifications.mine(FakeKeycloak.token(newCustomerId(), "CUSTOMER")).items())
        .isEmpty();
  }

  @Test
  void onlyStaffListByOrderRmaOrCustomer() {
    http.get()
        .uri("/notifications?orderId=o-1")
        .header("Authorization", "Bearer " + FakeKeycloak.token("c-1", "CUSTOMER"))
        .exchange()
        .expectStatus()
        .isForbidden();
    http.get()
        .uri("/notifications/me")
        .header("Authorization", "Bearer " + STAFF)
        .exchange()
        .expectStatus()
        .isForbidden();
    http.get().uri("/notifications/me").exchange().expectStatus().isUnauthorized();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"", "orderId=o-1&customerId=c-1", "size=0&orderId=o-1", "page=-1&rmaId=r-1"})
  void staffNameExactlyOneOfOrderRmaOrCustomerAndAPageThatExists(String query) {
    http.get()
        .uri("/notifications?" + query)
        .header("Authorization", "Bearer " + STAFF)
        .exchange()
        .expectStatus()
        .isBadRequest();
  }

  @Test
  void staffReadByRma() {
    assertThat(notifications.staffList(STAFF, "rmaId=" + newCustomerId()).items()).isEmpty();
  }

  /**
   * Waits until every event sent so far is consumed and its Notifications sent: the topic has one
   * partition, handled in order, so a marker Order's confirmation sent means every earlier event
   * was handled.
   */
  private void awaitEverythingConsumed() {
    var marker = newCustomerId();
    var address = "marker-" + marker + "@example.com";
    Keycloak.customer(marker, address, "Marker", "Customer");
    order(marker).line("VARIANT-1", 1, 100).placed().paid();
    email.awaitOneSentTo(address);
  }
}
