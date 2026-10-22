package com.ecomm.notifications;

import static com.ecomm.notifications.Events.payment;
import static com.ecomm.notifications.Events.shipment;
import static org.assertj.core.api.Assertions.assertThat;

import com.ecomm.commons.security.FakeKeycloak;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * A Customer's Notification preferences: email always on, SMS once they save an E.164 mobile number
 * and turn it on, push once they turn it on. An opted-in Customer's shipped, delivered and refunded
 * messages also go to the mock SMS and push providers, which Staff read.
 */
class PreferencesApiTest extends NotificationsApiTest {

  record Preferences(
      boolean email, boolean sms, String phoneNumber, boolean push, String updatedAt) {}

  record Mocked(
      String reference,
      String channel,
      String customerId,
      String to,
      String title,
      String text,
      String sentAt) {}

  record MockedPage(List<Mocked> items, int page, int size, long total) {}

  @Test
  void aCustomerWhoNeverSavedThemHasEmailOnly() {
    var mine = get(FakeKeycloak.token(newCustomerId(), "CUSTOMER"));

    assertThat(mine).isEqualTo(new Preferences(true, false, null, false, null));
  }

  @Test
  void aCustomerSavesAMobileNumberWithSmsAndPushOnAndReadsThemBack() {
    var token = FakeKeycloak.token(newCustomerId(), "CUSTOMER");

    var saved =
        put(token, Map.of("phoneNumber", "+49 151 12345678", "sms", true, "push", true))
            .expectStatus()
            .isOk()
            .expectBody(Preferences.class)
            .returnResult()
            .getResponseBody();

    assertThat(saved.phoneNumber()).isEqualTo("+4915112345678");
    assertThat(saved.updatedAt()).isNotNull();
    assertThat(get(token)).isEqualTo(saved);
    assertThat(saved)
        .extracting(Preferences::email, Preferences::sms, Preferences::push)
        .containsExactly(true, true, true);
  }

  @ParameterizedTest
  @ValueSource(strings = {"015112345678", "+0151123", "+49 151 abc", "+4915112345678901"})
  void aMobileNumberThatIsntE164IsRefused(String number) {
    var token = FakeKeycloak.token(newCustomerId(), "CUSTOMER");

    put(token, Map.of("phoneNumber", number, "sms", false, "push", false))
        .expectStatus()
        .isEqualTo(422)
        .expectBody()
        .jsonPath("$.reason")
        .isEqualTo("invalidPhoneNumber");
    assertThat(get(token).phoneNumber()).isNull();
  }

  @Test
  void smsWithoutAMobileNumberIsRefused() {
    put(FakeKeycloak.token(newCustomerId(), "CUSTOMER"), Map.of("sms", true, "push", false))
        .expectStatus()
        .isEqualTo(422)
        .expectBody()
        .jsonPath("$.reason")
        .isEqualTo("invalidPhoneNumber");
  }

  @Test
  void onlyACustomerReadsAndSavesTheirOwn() {
    http.get()
        .uri("/preferences/me")
        .header("Authorization", "Bearer " + STAFF)
        .exchange()
        .expectStatus()
        .isForbidden();
    http.get().uri("/preferences/me").exchange().expectStatus().isUnauthorized();
  }

  @Test
  void anOptedInCustomerIsAlsoSentEachShippingAndRefundMessageBySmsAndPush() {
    var customer = newCustomerId();
    var address = newAddress("ada");
    Keycloak.customer(customer, address, "Ada", "Lovelace");
    put(
            FakeKeycloak.token(customer, "CUSTOMER"),
            Map.of("phoneNumber", "+4915112345678", "sms", true, "push", true))
        .expectStatus()
        .isOk();
    var orderId = UUID.randomUUID().toString();
    var reference = "#" + orderId.substring(0, 8).toUpperCase();

    shipment(orderId, customer).created().shipped("Mock Carrier", "MC0012345678").delivered();
    payment(orderId, customer, 10000).authorized().captured().refunded(10000, null);

    var settled =
        new NotificationsClient(http)
            .awaitForOrder(
                STAFF,
                orderId,
                l -> l.size() == 9 && l.stream().allMatch(n -> n.status().equals("SENT")));
    assertThat(settled)
        .extracting(n -> n.messageType() + " " + n.channel())
        .containsExactlyInAnyOrder(
            "ORDER_SHIPPED EMAIL",
            "ORDER_SHIPPED SMS",
            "ORDER_SHIPPED PUSH",
            "ORDER_DELIVERED EMAIL",
            "ORDER_DELIVERED SMS",
            "ORDER_DELIVERED PUSH",
            "PAYMENT_REFUNDED EMAIL",
            "PAYMENT_REFUNDED SMS",
            "PAYMENT_REFUNDED PUSH");
    assertThat(settled)
        .filteredOn(n -> n.channel().equals("SMS"))
        .allSatisfy(n -> assertThat(n.recipient()).isEqualTo("+49***78"))
        .allSatisfy(n -> assertThat(n.message().text()).hasSizeLessThanOrEqualTo(160));
    var sms = mocked("sms", customer);
    assertThat(sms)
        .extracting(Mocked::text)
        .contains(
            "Ecomm: your Order " + reference + " has shipped. Mock Carrier tracking: MC0012345678");
    assertThat(sms).allSatisfy(m -> assertThat(m.to()).isEqualTo("+49***78"));
    var push = mocked("push", customer);
    assertThat(push).hasSize(3);
    assertThat(push)
        .extracting(Mocked::title)
        .contains("Your Order " + reference + " is on its way", "We've refunded €100.00");
    assertThat(email.sentTo(address)).hasSize(3);
  }

  @Test
  void aCustomerWhoTurnedSmsOffBeforeItWasSentIsntSentIt() {
    var customer = newCustomerId();
    Keycloak.customer(customer, newAddress("grace"), "Grace", "Hopper");
    var token = FakeKeycloak.token(customer, "CUSTOMER");
    put(token, Map.of("phoneNumber", "+4915112345678", "sms", true, "push", false))
        .expectStatus()
        .isOk();
    Keycloak.down();
    var orderId = UUID.randomUUID().toString();
    shipment(orderId, customer).created().shipped("Mock Carrier", "MC5");
    var client = new NotificationsClient(http);
    client.awaitForOrder(STAFF, orderId, l -> l.size() == 2);

    put(token, Map.of("phoneNumber", "+4915112345678", "sms", false, "push", false))
        .expectStatus()
        .isOk();
    Keycloak.reset();
    Keycloak.customer(customer, newAddress("grace"), "Grace", "Hopper");

    var sms =
        client
            .awaitForOrder(
                STAFF,
                orderId,
                l ->
                    l.stream()
                        .anyMatch(n -> n.channel().equals("SMS") && n.status().equals("FAILED")))
            .stream()
            .filter(n -> n.channel().equals("SMS"))
            .findFirst()
            .orElseThrow();
    assertThat(sms.attempts().getLast().reason()).isEqualTo("optedOut");
    assertThat(mocked("sms", customer)).isEmpty();
  }

  @Test
  void onlyStaffReadTheMockProvidersAndOnlySmsAndPushAreMocked() {
    http.get()
        .uri("/mock-providers/sms?customerId=c-1")
        .header("Authorization", "Bearer " + FakeKeycloak.token("c-1", "CUSTOMER"))
        .exchange()
        .expectStatus()
        .isForbidden();
    http.get()
        .uri("/mock-providers/email?customerId=c-1")
        .header("Authorization", "Bearer " + STAFF)
        .exchange()
        .expectStatus()
        .isNotFound();
  }

  private Preferences get(String token) {
    return http.get()
        .uri("/preferences/me")
        .header("Authorization", "Bearer " + token)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(Preferences.class)
        .returnResult()
        .getResponseBody();
  }

  private RestTestClient.ResponseSpec put(String token, Map<String, Object> body) {
    return http.put()
        .uri("/preferences/me")
        .header("Authorization", "Bearer " + token)
        .body(body)
        .exchange();
  }

  private List<Mocked> mocked(String channel, String customerId) {
    return http.get()
        .uri("/mock-providers/{channel}?customerId={customerId}", channel, customerId)
        .header("Authorization", "Bearer " + STAFF)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(new ParameterizedTypeReference<MockedPage>() {})
        .returnResult()
        .getResponseBody()
        .items();
  }
}
