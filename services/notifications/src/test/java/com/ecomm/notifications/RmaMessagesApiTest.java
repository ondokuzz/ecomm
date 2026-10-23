package com.ecomm.notifications;

import static com.ecomm.notifications.Events.rma;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.ecomm.commons.security.FakeKeycloak;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Each step of an RMA emails its Customer: requested, approved with the return address and
 * Send-back deadline, rejected with the reason, received, closed with the Inspection's note, and
 * expired. Staff read them on the RMA.
 */
class RmaMessagesApiTest extends NotificationsApiTest {

  NotificationsClient notifications;

  @BeforeEach
  void setUp() {
    notifications = new NotificationsClient(http);
  }

  @Test
  void anRmaRequestedIsEmailedWithItsReference() {
    var customer = newCustomerId();
    var address = newAddress("ada");
    Keycloak.customer(customer, address, "Ada", "Lovelace");
    var orderId = UUID.randomUUID().toString();
    var rma = rma(orderId, customer);

    rma.requested();

    var sent = email.awaitOneSentTo(address);
    assertThat(sent.message().subject())
        .isEqualTo("We've received your return request " + rma.reference());
    assertThat(sent.message().text())
        .contains("Hello Ada,")
        .contains("1 × Pixel 9")
        .contains("#" + orderId.substring(0, 8).toUpperCase());
    var notification =
        notifications
            .awaitForRma(STAFF, rma.rmaId(), l -> l.size() == 1 && l.getFirst().sentAt() != null)
            .getFirst();
    assertThat(notification.messageType()).isEqualTo("RMA_REQUESTED");
    assertThat(notification.channel()).isEqualTo("EMAIL");
    assertThat(notification.orderId()).isEqualTo(orderId);
  }

  @Test
  void anRmaApprovedIsEmailedWithTheReturnAddressAndSendBackDeadline() {
    var customer = newCustomerId();
    var address = newAddress("grace");
    Keycloak.customer(customer, address, "Grace", "Hopper");
    var rma = rma(UUID.randomUUID().toString(), customer).requested();
    email.awaitOneSentTo(address);

    rma.approved("WH-BER", "2026-11-06T12:05:00Z");

    await().atMost(Duration.ofSeconds(20)).until(() -> email.sentTo(address).size() == 2);
    var sent = email.sentTo(address).get(1);
    assertThat(sent.message().subject())
        .isEqualTo(
            "Your return " + rma.reference() + " is approved: send it back by November 6, 2026");
    assertThat(sent.message().text())
        .contains("Ecomm Returns Berlin")
        .contains("Lagerstraße 1")
        .contains("10115 Berlin")
        .contains("Germany")
        .contains("November 6, 2026")
        .contains("Write " + rma.reference() + " on the parcel");
    assertThat(sent.message().html()).contains("Lagerstraße 1").contains(rma.reference());
    assertThat(FulfillmentShipping.warehouseReads())
        .allSatisfy(
            r ->
                assertThat(r.getHeader("Authorization")).isEqualTo("Bearer notifications-token-1"));
  }

  @Test
  void anApprovalIsStillEmailedWhenTheWarehouseReadFailsAtFirst() {
    var customer = newCustomerId();
    var address = newAddress("katherine");
    Keycloak.customer(customer, address, "Katherine", "Johnson");
    FulfillmentShipping.failing(2);

    rma(UUID.randomUUID().toString(), customer).approved("WH-BER", "2026-11-06T12:05:00Z");

    var sent = email.awaitOneSentTo(address);
    assertThat(sent.message().text()).contains("Lagerstraße 1");
  }

  @Test
  void anApprovalWithoutAKnownWarehouseIsEmailedWithoutAnAddress() {
    var customer = newCustomerId();
    var address = newAddress("hedy");
    Keycloak.customer(customer, address, "Hedy", "Lamarr");
    var rma = rma(UUID.randomUUID().toString(), customer);

    rma.approved(null, "2026-11-06T12:05:00Z");
    var other = rma(UUID.randomUUID().toString(), customer);
    other.approved("WH-GONE", "2026-11-06T12:05:00Z");

    await().atMost(Duration.ofSeconds(20)).until(() -> email.sentTo(address).size() == 2);
    assertThat(email.sentTo(address))
        .allSatisfy(
            s ->
                assertThat(s.message().text())
                    .doesNotContain("Send it to")
                    .contains("Your RMA's page in your account shows where to send it"));
  }

  @Test
  void anRmaRejectedIsEmailedWithTheReason() {
    var customer = newCustomerId();
    var address = newAddress("barbara");
    Keycloak.customer(customer, address, "Barbara", "Liskov");
    var rma = rma(UUID.randomUUID().toString(), customer).warrantyClaim();

    rma.rejected("The damage isn't covered by the warranty.");

    var sent = email.awaitOneSentTo(address);
    assertThat(sent.message().subject())
        .isEqualTo("Your warranty claim " + rma.reference() + " was not approved");
    assertThat(sent.message().text()).contains("The damage isn't covered by the warranty.");
  }

  @Test
  void anRmaReceivedThenClosedIsEmailedEachTimeTheCloseWithTheInspectionsNote() {
    var customer = newCustomerId();
    var address = newAddress("frances");
    Keycloak.customer(customer, address, "Frances", "Allen");
    var rma = rma(UUID.randomUUID().toString(), customer);

    rma.received();
    email.awaitOneSentTo(address);
    rma.inspected("REFUSED", "The seal is broken and the box is missing.").resolved("CLOSED");

    await().atMost(Duration.ofSeconds(20)).until(() -> email.sentTo(address).size() == 2);
    var received = email.sentTo(address).get(0);
    assertThat(received.message().subject())
        .isEqualTo("We've received your return " + rma.reference());
    var closed = email.sentTo(address).get(1);
    assertThat(closed.message().subject())
        .isEqualTo("Your return " + rma.reference() + " is closed");
    assertThat(closed.message().text())
        .contains("The seal is broken and the box is missing.")
        .contains("no Refund");
  }

  @Test
  void anRmaExpiredIsEmailed() {
    var customer = newCustomerId();
    var address = newAddress("radia");
    Keycloak.customer(customer, address, "Radia", "Perlman");
    var rma = rma(UUID.randomUUID().toString(), customer);

    rma.approved("WH-BER", "2026-11-06T12:05:00Z");
    email.awaitOneSentTo(address);
    rma.expired();

    await().atMost(Duration.ofSeconds(20)).until(() -> email.sentTo(address).size() == 2);
    var expired = email.sentTo(address).get(1);
    assertThat(expired.message().subject())
        .isEqualTo("Your return " + rma.reference() + " has expired");
    assertThat(expired.message().text()).contains("November 6, 2026");
  }

  @Test
  void eachStepIsEmailedOnceAndRefundsExchangesAndHoldsAreNot() {
    var customer = newCustomerId();
    var address = newAddress("adele");
    Keycloak.customer(customer, address, "Adele", "Goldberg");
    var refunded = rma(UUID.randomUUID().toString(), customer);
    var exchanged = rma(UUID.randomUUID().toString(), customer).warrantyClaim();

    refunded.requested().redelivered().approved("WH-BER", "2026-11-06T12:05:00Z").redelivered();
    refunded.received().redelivered().inspected("ACCEPTED", null).resolved("REFUNDED");
    exchanged.received().inspected("FAULT_CONFIRMED", null).resolved("ON_HOLD");
    exchanged.resolved("EXCHANGED");

    var settled =
        notifications.awaitForRma(
            STAFF,
            refunded.rmaId(),
            l -> l.size() == 3 && l.stream().allMatch(n -> n.status().equals("SENT")));
    assertThat(settled)
        .extracting(NotificationsClient.NotificationView::messageType)
        .containsExactly("RMA_RECEIVED", "RMA_APPROVED", "RMA_REQUESTED");
    assertThat(settled).allSatisfy(n -> assertThat(n.channel()).isEqualTo("EMAIL"));
    var exchangedOnes =
        notifications.awaitForRma(
            STAFF, exchanged.rmaId(), l -> l.size() == 1 && l.getFirst().sentAt() != null);
    assertThat(exchangedOnes.getFirst().messageType()).isEqualTo("RMA_RECEIVED");
    assertThat(email.sentTo(address)).hasSize(4);
  }

  @Test
  void anOptedInCustomerIsStillOnlyEmailed() {
    var customer = newCustomerId();
    var address = newAddress("mary");
    Keycloak.customer(customer, address, "Mary", "Keller");
    http.put()
        .uri("/preferences/me")
        .header("Authorization", "Bearer " + FakeKeycloak.token(customer, "CUSTOMER"))
        .body(Map.of("phoneNumber", "+4915112345678", "sms", true, "push", true))
        .exchange()
        .expectStatus()
        .isOk();
    var rma = rma(UUID.randomUUID().toString(), customer);

    rma.requested();

    var recorded =
        notifications.awaitForRma(
            STAFF, rma.rmaId(), l -> l.size() == 1 && l.getFirst().sentAt() != null);
    assertThat(recorded.getFirst().channel()).isEqualTo("EMAIL");
  }
}
