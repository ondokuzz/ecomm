package com.ecomm.notifications;

import static com.ecomm.notifications.Events.abandonedCart;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.ecomm.commons.security.FakeKeycloak;
import com.ecomm.notifications.domain.UnsubscribeTokens;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * A Customer who turned cart reminders on is emailed once about each Abandoned Cart, listing the
 * Cart as Cart answers it now, with each line's current Price and Stock from Search & Discovery,
 * and a link to it. Nothing is recorded for a Customer who didn't turn them on, a Cart that is gone
 * or emptied, or one changed since it was abandoned.
 *
 * <p>Where nothing should be recorded, a reminder to another Customer is published after it: the
 * topic has one partition, so once that one is emailed, the first event has been handled too.
 */
class CartRemindersApiTest extends NotificationsApiTest {

  private static final Instant LEFT_AT = Instant.parse("2026-11-14T09:00:00Z");
  private static final Pattern REMINDERS =
      Pattern.compile(
          "(?m)^notifications_cart_reminders_total\\{[^}]*outcome=\"([^\"]+)\"[^}]*\\} (\\S+)$");
  private static final String UNSUBSCRIBE_PATH = "/unsubscribe/cart-reminders";
  private static final String UNSUBSCRIBE =
      "http://localhost:8080/api/notifications" + UNSUBSCRIBE_PATH;

  @Autowired JdbcClient jdbc;

  @Test
  void anOptedInCustomerIsEmailedTheirCartAsItIsNow() {
    var pixel = projectedVariant("Google Pixel 9", "storage", "128 GB");
    var speaker = projectedVariant("JBL Flip 6", null, null);
    var customer = optedIn("grace");
    SearchDiscovery.product(skuOf(pixel), "Google Pixel 9").variant(pixel, 79900, true).listed();
    SearchDiscovery.product(skuOf(speaker), "JBL Flip 6").variant(speaker, 12900, false).listed();
    Carts.cart(customer.id, LEFT_AT, items(pixel, 2, speaker, 1));

    abandonedCart(customer.id, LEFT_AT, items(pixel, 1)).publish();

    var sent = email.awaitOneSentTo(customer.address);
    assertThat(sent.message().subject()).isEqualTo("You left something in your Cart");
    assertThat(sent.message().text())
        .contains("Hello Grace,")
        .contains("2 × Google Pixel 9 (128 GB): €799.00 each")
        .contains("1 × JBL Flip 6: €129.00 each, out of Stock for now")
        .contains("http://localhost:8080/cart");
    assertThat(sent.message().html())
        .contains("Google Pixel 9 (128 GB)")
        .contains("href=\"http://localhost:8080/cart\"");
    assertThat(Carts.readsOf(customer.id))
        .allSatisfy(
            r ->
                assertThat(r.getHeader("Authorization")).isEqualTo("Bearer notifications-token-1"));
  }

  @Test
  void aLineWhosePriceChangedSinceTheCartWasLeftSaysSo() {
    var sku = newSku();
    var variant = sku + "-V";
    Events.product(sku, "Pixel 9", variant).price(79900).publish();
    awaitNamed(variant, "Pixel 9");
    Events.product(sku, "Pixel 9a", variant).version(2).price(69900).publish();
    awaitNamed(variant, "Pixel 9a");
    var customer = optedIn("hedy");
    SearchDiscovery.product(sku, "Pixel 9a").variant(variant, 69900, true).listed();
    var leftAt = Instant.now().minus(Duration.ofHours(1));
    Carts.cart(customer.id, leftAt, items(variant, 1));

    abandonedCart(customer.id, leftAt, items(variant, 1)).publish();

    assertThat(email.awaitOneSentTo(customer.address).message().text())
        .contains("1 × Pixel 9a: €699.00 each, its Price has changed");
  }

  @Test
  void aVariantProjectedBeforePricesWereKeptTakesItsPriceFromItsReplayedEvent() {
    var sku = newSku();
    var variant = sku + "-V";
    Events.product(sku, "Pixel 9", variant).price(79900).publish();
    awaitNamed(variant, "Pixel 9");
    jdbc.sql(
            "UPDATE product_variant SET price_minor = NULL, price_currency = NULL WHERE sku = :sku")
        .param("sku", sku)
        .update();

    // Replayed from the start, the version held is applied again, now that it has no Price.
    Events.product(sku, "Pixel 9", variant).price(79900).publish();
    Events.product(sku, "Pixel 9a", variant).version(2).price(69900).publish();
    awaitNamed(variant, "Pixel 9a");
    var customer = optedIn("vera");
    SearchDiscovery.product(sku, "Pixel 9a").variant(variant, 69900, true).listed();
    var leftAt = Instant.now().minus(Duration.ofHours(1));
    Carts.cart(customer.id, leftAt, items(variant, 1));

    abandonedCart(customer.id, leftAt, items(variant, 1)).publish();

    assertThat(email.awaitOneSentTo(customer.address).message().text())
        .contains("1 × Pixel 9a: €699.00 each, its Price has changed");
  }

  @Test
  void aLineNoLongerSoldIsLeftOut() {
    var kept = projectedVariant("Google Pixel 9", null, null);
    var removed = projectedVariant("Old Phone", null, null);
    var dropped = projectedVariant("Old Colour", null, null);
    var customer = optedIn("ida");
    SearchDiscovery.product(skuOf(kept), "Google Pixel 9").variant(kept, 79900, true).listed();
    SearchDiscovery.product(skuOf(dropped), "Old Colour").variant("ANOTHER-V", 100, true).listed();
    Carts.cart(customer.id, LEFT_AT, items(kept, 1, removed, 1, dropped, 1));

    abandonedCart(customer.id, LEFT_AT, items(kept, 1)).publish();

    assertThat(email.awaitOneSentTo(customer.address).message().text())
        .contains("1 × Google Pixel 9: €799.00 each")
        .doesNotContain("Old Phone")
        .doesNotContain("Old Colour");
  }

  @Test
  void aCartAllOfWhoseLinesAreGoneRecordsNothing() {
    var removed = projectedVariant("Old Phone", null, null);
    var customer = optedIn("joan");
    Carts.cart(customer.id, LEFT_AT, items(removed, 1));

    abandonedCart(customer.id, LEFT_AT, items(removed, 1)).publish();

    assertNothingRecordedFor(customer);
  }

  @Test
  void aCustomerWhoDidNotTurnRemindersOnGetsNone() {
    var pixel = projectedVariant("Google Pixel 9", null, null);
    SearchDiscovery.product(skuOf(pixel), "Google Pixel 9").variant(pixel, 79900, true).listed();
    var never = customer("karen");
    var turnedOff = customer("lise");
    save(turnedOff, true);
    save(turnedOff, false);
    Carts.cart(never.id, LEFT_AT, items(pixel, 1));
    Carts.cart(turnedOff.id, LEFT_AT, items(pixel, 1));

    abandonedCart(never.id, LEFT_AT, items(pixel, 1)).publish();
    abandonedCart(turnedOff.id, LEFT_AT, items(pixel, 1)).publish();

    assertNothingRecordedFor(never, turnedOff);
    assertThat(Carts.readsOf(never.id)).isEmpty();
  }

  @Test
  void aCartThatIsGoneOrEmptiedRecordsNothing() {
    var pixel = projectedVariant("Google Pixel 9", null, null);
    var customer = optedIn("mary");

    abandonedCart(customer.id, LEFT_AT, items(pixel, 1)).publish();

    assertNothingRecordedFor(customer);
    assertThat(Carts.readsOf(customer.id)).hasSize(1);
  }

  @Test
  void aCartChangedSinceItWasAbandonedRecordsNothing() {
    var pixel = projectedVariant("Google Pixel 9", null, null);
    SearchDiscovery.product(skuOf(pixel), "Google Pixel 9").variant(pixel, 79900, true).listed();
    var customer = optedIn("nora");
    Carts.cart(customer.id, LEFT_AT.plus(Duration.ofMinutes(10)), items(pixel, 3));

    abandonedCart(customer.id, LEFT_AT, items(pixel, 1)).publish();

    assertNothingRecordedFor(customer);
  }

  @Test
  void anAbandonmentDeliveredTwiceIsOneEmailAndNeverAnSmsOrPush() {
    var pixel = projectedVariant("Google Pixel 9", null, null);
    SearchDiscovery.product(skuOf(pixel), "Google Pixel 9").variant(pixel, 79900, true).listed();
    var customer = customer("olga");
    put(
        customer,
        Map.of("phoneNumber", "+4915112345678", "sms", true, "push", true, "cartReminders", true));
    Carts.cart(customer.id, LEFT_AT, items(pixel, 1));

    abandonedCart(customer.id, LEFT_AT, items(pixel, 1)).publish().publish();

    email.awaitOneSentTo(customer.address);
    assertNothingMoreRecordedFor(customer, 1);
    assertThat(email.sentTo(customer.address)).hasSize(1);
    assertThat(ledgerOf(customer).items())
        .extracting(
            NotificationsClient.NotificationView::messageType,
            NotificationsClient.NotificationView::channel)
        .containsExactly(org.assertj.core.groups.Tuple.tuple("CART_ABANDONED", "EMAIL"));
  }

  @Test
  void aReminderIsStillSentWhenCartsReadFailsAtFirst() {
    var pixel = projectedVariant("Google Pixel 9", null, null);
    SearchDiscovery.product(skuOf(pixel), "Google Pixel 9").variant(pixel, 79900, true).listed();
    var customer = optedIn("pat");
    Carts.failingThen(2, customer.id, LEFT_AT, items(pixel, 1));

    abandonedCart(customer.id, LEFT_AT, items(pixel, 1)).publish();

    assertThat(email.awaitOneSentTo(customer.address).message().text())
        .contains("1 × Google Pixel 9: €799.00 each");
  }

  @Test
  void theRemindersOneClickUnsubscribeTurnsRemindersOffWithoutSigningIn() {
    var customer = reminded("rosa");
    var sent = email.awaitOneSentTo(customer.address).message();
    var link = sent.unsubscribeUrl();
    assertThat(link).startsWith(UNSUBSCRIBE + "?token=");
    assertThat(sent.text()).contains(link);
    assertThat(sent.html()).contains(tokenOf(link));

    oneClick(tokenOf(link))
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentTypeCompatibleWith(MediaType.TEXT_HTML)
        .expectBody(String.class)
        .value(page -> assertThat(page).contains("Cart reminders are off"));

    assertThat(preferencesOf(customer).get("cartReminders")).isEqualTo(false);
  }

  @Test
  void staffReadingTheLedgerDoNotSeeTheLinksToken() {
    var customer = reminded("ruth");
    var token = tokenOf(email.awaitOneSentTo(customer.address).message().unsubscribeUrl());

    var stored =
        await()
            .atMost(Duration.ofSeconds(20))
            .until(
                () -> ledgerOf(customer).items(),
                l -> l.size() == 1 && l.getFirst().message() != null)
            .getFirst()
            .message();

    assertThat(stored.text()).doesNotContain(token).contains("/unsubscribe/cart-reminders?token=…");
    assertThat(stored.html()).doesNotContain(token);
  }

  @Test
  void theLinksPageAsksToConfirmAndChangesNothing() {
    var customer = optedIn("sara");
    var token = new UnsubscribeTokens(UNSUBSCRIBE_KEY).issue(customer.id, Instant.now());

    http.get()
        .uri(UNSUBSCRIBE_PATH + "?token={token}", token)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(String.class)
        .value(
            page ->
                assertThat(page)
                    .contains("<form method=\"post\">")
                    .contains("Turn off cart reminders"));

    assertThat(preferencesOf(customer).get("cartReminders")).isEqualTo(true);
  }

  @Test
  void usingTheLinkAgainIsHarmless() {
    var customer = optedIn("tess");
    put(
        customer,
        Map.of("phoneNumber", "+4915112345678", "sms", true, "push", true, "cartReminders", true));
    var token = new UnsubscribeTokens(UNSUBSCRIBE_KEY).issue(customer.id, Instant.now());

    oneClick(token).expectStatus().isOk();
    var after = preferencesOf(customer);
    oneClick(token).expectStatus().isOk();

    assertThat(preferencesOf(customer)).isEqualTo(after);
    assertThat(after)
        .containsEntry("cartReminders", false)
        .containsEntry("sms", true)
        .containsEntry("phoneNumber", "+4915112345678")
        .containsEntry("push", true);
  }

  @Test
  void aTamperedTokenIsRefusedWithAPageSayingToSignIn() {
    var customer = optedIn("uma");
    var other = optedIn("una");
    var token = new UnsubscribeTokens(UNSUBSCRIBE_KEY).issue(customer.id, Instant.now());
    var othersPayload =
        new UnsubscribeTokens(UNSUBSCRIBE_KEY).issue(other.id, Instant.now()).split("\\.")[0];
    var forged = othersPayload + "." + token.split("\\.")[1];
    var signedElsewhere = new UnsubscribeTokens("another-key").issue(other.id, Instant.now());

    for (var bad : new String[] {forged, signedElsewhere, "not-a-token", ""}) {
      oneClick(bad)
          .expectStatus()
          .isBadRequest()
          .expectHeader()
          .contentTypeCompatibleWith(MediaType.TEXT_HTML)
          .expectBody(String.class)
          .value(page -> assertThat(page).contains("sign in"));
      http.get()
          .uri(UNSUBSCRIBE_PATH + "?token={token}", bad)
          .exchange()
          .expectStatus()
          .isBadRequest();
    }

    assertThat(preferencesOf(customer).get("cartReminders")).isEqualTo(true);
    assertThat(preferencesOf(other).get("cartReminders")).isEqualTo(true);
  }

  @Test
  void aTokenOlderThan30DaysIsRefused() {
    var customer = optedIn("vida");
    var tokens = new UnsubscribeTokens(UNSUBSCRIBE_KEY);
    var expired = tokens.issue(customer.id, Instant.now().minus(Duration.ofDays(30)));

    oneClick(expired)
        .expectStatus()
        .isBadRequest()
        .expectBody(String.class)
        .value(page -> assertThat(page).contains("expired"));
    assertThat(preferencesOf(customer).get("cartReminders")).isEqualTo(true);

    oneClick(tokens.issue(customer.id, Instant.now().minus(Duration.ofDays(29))))
        .expectStatus()
        .isOk();
    assertThat(preferencesOf(customer).get("cartReminders")).isEqualTo(false);
  }

  @Test
  void eachAbandonmentIsCountedByWhatCameOfIt() {
    var before = reminderCounts();
    var pixel = projectedVariant("Google Pixel 9", null, null);
    SearchDiscovery.product(skuOf(pixel), "Google Pixel 9").variant(pixel, 79900, true).listed();
    var removed = projectedVariant("Old Phone", null, null);
    var optedOut = customer("wren");
    var changed = optedIn("xena");
    var gone = optedIn("yara");
    var unsold = optedIn("zoe");
    Carts.cart(optedOut.id, LEFT_AT, items(pixel, 1));
    Carts.cart(changed.id, LEFT_AT.plus(Duration.ofMinutes(10)), items(pixel, 1));
    Carts.cart(unsold.id, LEFT_AT, items(removed, 1));

    for (var customer : new Customer[] {optedOut, changed, gone, unsold}) {
      abandonedCart(customer.id, LEFT_AT, items(pixel, 1)).publish();
    }
    var sent = optedIn("abby");
    Carts.cart(sent.id, LEFT_AT, items(pixel, 1));
    abandonedCart(sent.id, LEFT_AT, items(pixel, 1))
        .publish()
        .publish(); // the second isn't counted
    sentinel();

    var after = reminderCounts();
    assertThat(after.get("sent") - before.get("sent")).isEqualTo(2); // abby's and the sentinel's
    assertThat(after.get("skippedOptedOut") - before.get("skippedOptedOut")).isEqualTo(1);
    assertThat(after.get("skippedChanged") - before.get("skippedChanged")).isEqualTo(1);
    assertThat(after.get("skippedEmpty") - before.get("skippedEmpty")).isEqualTo(2);
  }

  record Customer(String id, String address, String token) {}

  private Customer customer(String name) {
    var id = newCustomerId();
    var address = newAddress(name);
    Keycloak.customer(
        id, address, Character.toUpperCase(name.charAt(0)) + name.substring(1), "Tester");
    return new Customer(id, address, FakeKeycloak.token(id, "CUSTOMER"));
  }

  private Customer optedIn(String name) {
    var customer = customer(name);
    save(customer, true);
    return customer;
  }

  private void save(Customer customer, boolean cartReminders) {
    put(customer, Map.of("sms", false, "push", false, "cartReminders", cartReminders));
  }

  private void put(Customer customer, Map<String, Object> preferences) {
    http.put()
        .uri("/preferences/me")
        .header("Authorization", "Bearer " + customer.token)
        .body(preferences)
        .exchange()
        .expectStatus()
        .isOk();
  }

  /** An opted-in Customer whose Abandoned Cart was published, so a reminder is on its way. */
  private Customer reminded(String name) {
    var pixel = projectedVariant("Google Pixel 9", null, null);
    SearchDiscovery.product(skuOf(pixel), "Google Pixel 9").variant(pixel, 79900, true).listed();
    var customer = optedIn(name);
    Carts.cart(customer.id, LEFT_AT, items(pixel, 1));
    abandonedCart(customer.id, LEFT_AT, items(pixel, 1)).publish();
    return customer;
  }

  /** What a mail client sends for RFC 8058's one-click unsubscribe: no token, no cookie. */
  private RestTestClient.ResponseSpec oneClick(String token) {
    return http.post()
        .uri(UNSUBSCRIBE_PATH + "?token={token}", token)
        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
        .body("List-Unsubscribe=One-Click")
        .exchange();
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> preferencesOf(Customer customer) {
    return http.get()
        .uri("/preferences/me")
        .header("Authorization", "Bearer " + customer.token)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(Map.class)
        .returnResult()
        .getResponseBody();
  }

  private static String tokenOf(String link) {
    return link.substring(link.indexOf("?token=") + "?token=".length());
  }

  /** {@code notifications_cart_reminders_total} by outcome, as Prometheus scrapes it. */
  private Map<String, Double> reminderCounts() {
    var scrape =
        http.get()
            .uri("/actuator/prometheus")
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(String.class)
            .returnResult()
            .getResponseBody();
    var counts = new HashMap<String, Double>();
    var matcher = REMINDERS.matcher(scrape);
    while (matcher.find()) {
      counts.put(matcher.group(1), Double.parseDouble(matcher.group(2)));
    }
    assertThat(counts)
        .as("notifications_cart_reminders_total in the scrape, from 0")
        .containsOnlyKeys("sent", "skippedOptedOut", "skippedChanged", "skippedEmpty");
    return counts;
  }

  /** Each Customer's events handled, with nothing recorded for any of them. */
  private void assertNothingRecordedFor(Customer... customers) {
    sentinel();
    for (var customer : customers) {
      assertThat(ledgerOf(customer).total()).isZero();
      assertThat(email.sentTo(customer.address)).isEmpty();
    }
  }

  /** The Customer's events handled, with {@code count} Notifications recorded for them. */
  private void assertNothingMoreRecordedFor(Customer customer, int count) {
    sentinel();
    assertThat(ledgerOf(customer).total()).isEqualTo(count);
  }

  /** Publishes a reminder to a new Customer behind what was published, and waits for its email. */
  private void sentinel() {
    var pixel = projectedVariant("Sentinel Phone", null, null);
    SearchDiscovery.product(skuOf(pixel), "Sentinel Phone").variant(pixel, 100, true).listed();
    var sentinel = optedIn("sentinel");
    Carts.cart(sentinel.id, LEFT_AT, items(pixel, 1));
    abandonedCart(sentinel.id, LEFT_AT, items(pixel, 1)).publish();
    email.awaitOneSentTo(sentinel.address);
  }

  private NotificationsClient.Page ledgerOf(Customer customer) {
    return new NotificationsClient(http).staffList(STAFF, "customerId=" + customer.id);
  }

  private static String skuOf(String variantId) {
    return variantId.substring(0, variantId.length() - "-V".length());
  }

  private static Map<String, Integer> items(Object... variantsAndQuantities) {
    var items = new LinkedHashMap<String, Integer>();
    for (var i = 0; i < variantsAndQuantities.length; i += 2) {
      items.put((String) variantsAndQuantities[i], (Integer) variantsAndQuantities[i + 1]);
    }
    return items;
  }
}
