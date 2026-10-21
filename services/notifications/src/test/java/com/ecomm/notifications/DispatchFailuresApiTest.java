package com.ecomm.notifications;

import static com.ecomm.notifications.Events.order;
import static org.assertj.core.api.Assertions.assertThat;

import com.ecomm.notifications.NotificationsClient.AttemptView;
import com.ecomm.notifications.domain.SendOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * A transient failure is retried with backoff, six attempts in all, then the Notification is {@code
 * FAILED}; a permanent one fails it at once. A Customer Keycloak doesn't know, or one with no email
 * address, is {@code noRecipient}; Keycloak not answering is transient.
 */
class DispatchFailuresApiTest extends NotificationsApiTest {

  NotificationsClient notifications;

  @BeforeEach
  void setUp() {
    notifications = new NotificationsClient(http);
  }

  @Test
  void aTransientFailureIsRetriedSixTimesThenFailed() {
    var customer = newCustomerId();
    var address = newAddress("flaky");
    Keycloak.customer(customer, address, "Flaky", "Mailbox");
    email.failFor(address, SendOutcome.Failed.transientFailure("smtp 451: try later"));

    var order = order(customer).line("VARIANT-1", 1, 2500).placed().paid();

    var notification = notifications.awaitSettled(STAFF, order.orderId());
    assertThat(notification.status()).isEqualTo("FAILED");
    assertThat(notification.attempts())
        .extracting(AttemptView::outcome)
        .containsExactly(
            "TRANSIENT_FAILURE",
            "TRANSIENT_FAILURE",
            "TRANSIENT_FAILURE",
            "TRANSIENT_FAILURE",
            "TRANSIENT_FAILURE",
            "TRANSIENT_FAILURE");
    assertThat(notification.attempts().getLast().reason()).isEqualTo("smtp 451: try later");
    assertThat(notification.message().subject()).contains(order.reference());
    assertThat(email.sentTo(address)).hasSize(6);
  }

  @Test
  void aPermanentFailureFailsAtOnce() {
    var customer = newCustomerId();
    var address = newAddress("gone");
    Keycloak.customer(customer, address, "Gone", "Away");
    email.failFor(address, SendOutcome.Failed.permanent("smtp 550: no such user"));

    var order = order(customer).line("VARIANT-1", 1, 2500).placed().paid();

    var notification = notifications.awaitSettled(STAFF, order.orderId());
    assertThat(notification.status()).isEqualTo("FAILED");
    assertThat(notification.attempts()).hasSize(1);
    assertThat(notification.attempts().getFirst().outcome()).isEqualTo("PERMANENT_FAILURE");
    assertThat(notification.attempts().getFirst().reason()).isEqualTo("smtp 550: no such user");
    assertThat(notification.recipient()).startsWith("g***@");
  }

  @Test
  void aCustomerKeycloakDoesNotKnowIsNoRecipient() {
    var order = order(newCustomerId()).line("VARIANT-1", 1, 2500).placed().paid();

    var notification = notifications.awaitSettled(STAFF, order.orderId());

    assertThat(notification.status()).isEqualTo("FAILED");
    assertThat(notification.attempts())
        .singleElement()
        .extracting(AttemptView::reason)
        .isEqualTo("noRecipient");
    assertThat(notification.recipient()).isNull();
    assertThat(notification.message()).isNull();
  }

  @Test
  void aCustomerWithNoEmailAddressIsNoRecipient() {
    var customer = newCustomerId();
    Keycloak.customer(customer, null, "No", "Address");

    var order = order(customer).line("VARIANT-1", 1, 2500).placed().paid();

    var notification = notifications.awaitSettled(STAFF, order.orderId());
    assertThat(notification.status()).isEqualTo("FAILED");
    assertThat(notification.attempts())
        .singleElement()
        .extracting(AttemptView::reason)
        .isEqualTo("noRecipient");
  }

  @Test
  void keycloakDownIsTransientAndTheNotificationIsSentOnceItIsBack() {
    var customer = newCustomerId();
    Keycloak.down();

    var order = order(customer).line("VARIANT-1", 1, 2500).placed().paid();
    notifications.awaitForOrder(
        STAFF, order.orderId(), l -> l.size() == 1 && l.getFirst().attempts().size() >= 2);
    Keycloak.reset();
    Keycloak.customer(customer, "patient@example.com", "Patient", "Customer");

    var notification = notifications.awaitSettled(STAFF, order.orderId());
    assertThat(notification.status()).isEqualTo("SENT");
    assertThat(notification.attempts().getFirst().outcome()).isEqualTo("TRANSIENT_FAILURE");
    assertThat(notification.attempts().getFirst().reason()).isEqualTo("directoryUnavailable");
    assertThat(notification.attempts().getLast().outcome()).isEqualTo("SENT");
  }
}
