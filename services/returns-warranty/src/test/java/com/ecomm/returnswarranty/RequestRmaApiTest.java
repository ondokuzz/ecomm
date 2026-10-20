package com.ecomm.returnswarranty;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * A Customer requests an RMA for a delivered Variant of their Order, and every way it's refused.
 */
class RequestRmaApiTest extends RmasApiTest {

  /** Delivered 20 days before the fixed clock: after the Return Period, within any warranty. */
  private static final java.time.Instant LAST_MONTH = TestTimeSource.NOW.minus(Duration.ofDays(20));

  @Test
  void aReturnOfADeliveredPhoneNamingItsImeiIsAccepted() {
    var phone = newVariant("PHONE");
    var imei = newImei();
    var shipment =
        Shipments.shipment(CUSTOMER_ID)
            .line(phone, "Galaxy S25", 1, "IMEI", imei)
            .delivered(RECENTLY);

    var result = requestedOnceProjected(rma(shipment.orderId(), phone, 1, "RETURN", imei));

    assertThat(result.getStatus().value()).isEqualTo(201);
    var rma = readRma(CUSTOMER, result.getResponseHeaders().getLocation().getPath().substring(6));
    assertThat(rma.reference()).isEqualTo("RMA-" + rma.id().substring(0, 8).toUpperCase());
    assertThat(rma.orderId()).isEqualTo(shipment.orderId());
    assertThat(rma.customerId()).isEqualTo(CUSTOMER_ID);
    assertThat(rma.variantId()).isEqualTo(phone);
    assertThat(rma.productName()).isEqualTo("Galaxy S25");
    assertThat(rma.quantity()).isEqualTo(1);
    assertThat(rma.serialNumbers()).containsExactly(imei);
    assertThat(rma.kind()).isEqualTo("RETURN");
    assertThat(rma.reason()).isEqualTo("It stopped charging");
    assertThat(rma.status()).isEqualTo("REQUESTED");
    assertThat(rma.requestedAt()).isEqualTo("2026-10-15T12:00:00Z");
    assertThat(rma.window())
        .isEqualTo(
            new WindowView("RETURN_PERIOD", "2026-10-12T12:00:00Z", "2026-10-26T12:00:00Z", null));
    assertThat(rma.history())
        .containsExactly(new HistoryView("REQUESTED", "CUSTOMER", "2026-10-15T12:00:00Z", null));
    assertThat(rma.refundAmount()).isEqualTo(new MoneyView(1000, "EUR"));
  }

  @Test
  void aWarrantyClaimAfterTheReturnPeriodWithinTheWarrantyWindowIsAccepted() {
    var phone = newVariant("PHONE");
    Products.product("Galaxy S25", phone).tracked("IMEI").warranty(24, "Samsung").publish();
    var imei = newImei();
    var shipment =
        Shipments.shipment(CUSTOMER_ID)
            .line(phone, "Galaxy S25", 1, "IMEI", imei)
            .delivered(LAST_MONTH);

    var rma = requested(rma(shipment.orderId(), phone, 1, "WARRANTY_CLAIM", imei));

    assertThat(rma.kind()).isEqualTo("WARRANTY_CLAIM");
    assertThat(rma.window())
        .isEqualTo(
            new WindowView(
                "WARRANTY_WINDOW",
                "2026-09-25T12:00:00Z",
                "2028-09-25T12:00:00Z",
                Map.of("months", 24, "manufacturer", "Samsung")));
  }

  @Test
  void theRecordedWindowSurvivesACatalogChange() {
    var laptop = newVariant("LAPTOP");
    var product = Products.product("ThinkPad X1", laptop).tracked("SERIAL").warranty(24, "Lenovo");
    product.publish();
    var shipment =
        Shipments.shipment(CUSTOMER_ID)
            .line(laptop, "ThinkPad X1", 2, "SERIAL", "SN-AAA1", "SN-BBB2")
            .delivered(LAST_MONTH);
    var first = requested(rma(shipment.orderId(), laptop, 1, "WARRANTY_CLAIM", "SN-AAA1"));

    product.warranty(12, "Lenovo Services").version(2).publish();
    awaitProductsProjected();

    var second = requested(rma(shipment.orderId(), laptop, 1, "WARRANTY_CLAIM", "SN-BBB2"));

    assertThat(second.window().warranty())
        .isEqualTo(Map.of("months", 12, "manufacturer", "Lenovo Services"));

    assertThat(readRma(CUSTOMER, first.id()).window()).isEqualTo(first.window());
    assertThat(first.window().warranty()).isEqualTo(Map.of("months", 24, "manufacturer", "Lenovo"));
  }

  @Test
  void anOrderNotYetDeliveredIsNotFound() {
    var phone = newVariant("PHONE");
    var shipment =
        Shipments.shipment(CUSTOMER_ID).line(phone, "Galaxy S25", 1, "NONE").at(3, "PACKED");
    awaitShipmentsProjected();

    request(CUSTOMER, rma(shipment.orderId(), phone, 1, "RETURN")).expectStatus().isNotFound();
  }

  @Test
  void anotherCustomersOrderIsNotFound() {
    var phone = newVariant("PHONE");
    var shipment =
        Shipments.shipment("customer-43").line(phone, "Galaxy S25", 1, "NONE").delivered(RECENTLY);
    awaitShipmentsProjected();

    request(CUSTOMER, rma(shipment.orderId(), phone, 1, "RETURN")).expectStatus().isNotFound();
  }

  @Test
  void anUnknownOrderIsNotFound() {
    request(CUSTOMER, rma("no-such-order", "V-1", 1, "RETURN")).expectStatus().isNotFound();
  }

  @Test
  void moreUnitsThanAreLeftToClaimAreRefused() {
    var charger = newVariant("CHARGER");
    var shipment =
        Shipments.shipment(CUSTOMER_ID).line(charger, "Charger", 3, "NONE").delivered(RECENTLY);
    requested(rma(shipment.orderId(), charger, 2, "RETURN"));

    var problem = refused(rma(shipment.orderId(), charger, 2, "RETURN"));

    assertThat(problem.get("reason").asString()).isEqualTo("quantityExceeded");
    assertThat(problem.get("available").asInt()).isEqualTo(1);
  }

  @Test
  void aSerialNumberNotShippedOnTheOrderIsRefusedByName() {
    var phone = newVariant("PHONE");
    var shipment =
        Shipments.shipment(CUSTOMER_ID)
            .line(phone, "Galaxy S25", 1, "IMEI", newImei())
            .delivered(RECENTLY);
    var stranger = newImei();

    var problem = refused(rma(shipment.orderId(), phone, 1, "RETURN", stranger));

    assertThat(problem.get("reason").asString()).isEqualTo("serialNotShipped");
    assertThat(problem.get("serialNumber").asString()).isEqualTo(stranger);
  }

  @Test
  void aSerialNumberAlreadyInAnOpenRmaIsRefusedByName() {
    var phone = newVariant("PHONE");
    var imei = newImei();
    var shipment =
        Shipments.shipment(CUSTOMER_ID)
            .line(phone, "Galaxy S25", 2, "IMEI", imei, newImei())
            .delivered(RECENTLY);
    requested(rma(shipment.orderId(), phone, 1, "RETURN", imei));

    var problem = refused(rma(shipment.orderId(), phone, 1, "RETURN", imei));

    assertThat(problem.get("reason").asString()).isEqualTo("serialAlreadyClaimed");
    assertThat(problem.get("serialNumber").asString()).isEqualTo(imei);
  }

  @Test
  void aSerialTrackedVariantNeedsOneSerialNumberPerUnit() {
    var phone = newVariant("PHONE");
    var shipment =
        Shipments.shipment(CUSTOMER_ID)
            .line(phone, "Galaxy S25", 2, "IMEI", newImei(), newImei())
            .delivered(RECENTLY);

    var problem = refused(rma(shipment.orderId(), phone, 2, "RETURN"));

    assertThat(problem.get("reason").asString()).isEqualTo("serialNumbersMiscounted");
    assertThat(problem.get("expected").asInt()).isEqualTo(2);
  }

  @Test
  void aReturnAfterTheReturnPeriodIsRefusedWithTheDateItEnded() {
    var charger = newVariant("CHARGER");
    var shipment =
        Shipments.shipment(CUSTOMER_ID).line(charger, "Charger", 1, "NONE").delivered(LAST_MONTH);

    var problem = refused(rma(shipment.orderId(), charger, 1, "RETURN"));

    assertThat(problem.get("reason").asString()).isEqualTo("outsideReturnPeriod");
    assertThat(problem.get("endedAt").asString()).isEqualTo("2026-10-09T12:00:00Z");
  }

  @Test
  void aWarrantyClaimAfterTheWarrantyWindowIsRefusedWithTheDateItEnded() {
    var earbuds = newVariant("EARBUDS");
    Products.product("Galaxy Buds", earbuds).warranty(12, "Samsung").publish();
    var shipment =
        Shipments.shipment(CUSTOMER_ID)
            .line(earbuds, "Galaxy Buds", 1, "NONE")
            .delivered(java.time.Instant.parse("2025-10-15T12:00:00Z"));

    var problem = refused(rma(shipment.orderId(), earbuds, 1, "WARRANTY_CLAIM"));

    assertThat(problem.get("reason").asString()).isEqualTo("outsideWarrantyWindow");
    assertThat(problem.get("endedAt").asString()).isEqualTo("2026-10-15T12:00:00Z");
  }

  @Test
  void aWarrantyClaimOnAProductWithoutWarrantyTermsIsRefused() {
    var cable = newVariant("CABLE");
    Products.product("USB-C cable", cable).publish();
    var shipment =
        Shipments.shipment(CUSTOMER_ID).line(cable, "USB-C cable", 1, "NONE").delivered(RECENTLY);

    assertThat(
            refused(rma(shipment.orderId(), cable, 1, "WARRANTY_CLAIM")).get("reason").asString())
        .isEqualTo("noWarranty");
  }

  @Test
  void aWarrantyClaimBeforeItsProductIsKnownIsToBeRetried() {
    var unknown = newVariant("UNKNOWN");
    var shipment =
        Shipments.shipment(CUSTOMER_ID).line(unknown, "Mystery", 1, "NONE").delivered(RECENTLY);
    awaitShipmentsProjected();

    request(CUSTOMER, rma(shipment.orderId(), unknown, 1, "WARRANTY_CLAIM"))
        .expectStatus()
        .isEqualTo(503)
        .expectHeader()
        .exists("Retry-After")
        .expectBody()
        .jsonPath("$.reason")
        .isEqualTo("productNotKnownYet");
  }

  @Test
  void anRmaBeforeItsOrderIsKnownIsToBeRetried() {
    var charger = newVariant("CHARGER");
    var shipment =
        Shipments.shipment(CUSTOMER_ID)
            .line(charger, "Charger", 1, "NONE")
            .withoutOrder()
            .delivered(RECENTLY);
    awaitShipmentsProjected();

    request(CUSTOMER, rma(shipment.orderId(), charger, 1, "RETURN"))
        .expectStatus()
        .isEqualTo(503)
        .expectHeader()
        .exists("Retry-After")
        .expectBody()
        .jsonPath("$.reason")
        .isEqualTo("orderNotKnownYet");

    Orders.order(shipment.orderId(), CUSTOMER_ID).line(charger, 1, 1999).publish();

    assertThat(requested(rma(shipment.orderId(), charger, 1, "RETURN")).refundAmount())
        .isEqualTo(new MoneyView(1999, "EUR"));
  }

  @Test
  void theRefundAmountIsTheUnitsPriceLessDiscountsPlusTaxAndNeverTheShipping() {
    var headphones = newVariant("HEADPHONES");
    var theCase = newVariant("CASE");
    var shipment =
        Shipments.shipment(CUSTOMER_ID)
            .line(headphones, "WH-1000XM5", 2, "NONE")
            .line(theCase, "Case", 1, "NONE")
            .withoutOrder();
    // 25.00 of lines, 2.50 off and 4.76 of Tax: the headphones come to 21.81, the case to 5.45.
    Orders.order(shipment.orderId(), CUSTOMER_ID)
        .line(headphones, 2, 1000)
        .line(theCase, 1, 500)
        .coupon(250)
        .tax(476)
        .publish();
    shipment.delivered(RECENTLY);

    var first = requested(rma(shipment.orderId(), headphones, 1, "RETURN"));
    var second = requested(rma(shipment.orderId(), headphones, 1, "RETURN"));
    var last = requested(rma(shipment.orderId(), theCase, 1, "RETURN"));

    assertThat(first.refundAmount()).isEqualTo(new MoneyView(1090, "EUR"));
    assertThat(second.refundAmount()).isEqualTo(new MoneyView(1091, "EUR"));
    assertThat(last.refundAmount()).isEqualTo(new MoneyView(545, "EUR"));
  }

  @Test
  void aMalformedRequestNamesTheField() {
    request(CUSTOMER, rma("order-1", "V-1", 0, "RETURN"))
        .expectStatus()
        .isBadRequest()
        .expectBody()
        .jsonPath("$.errors[0].field")
        .isEqualTo("quantity");
    request(CUSTOMER, rma("order-1", "V-1", 1, "EXCHANGE"))
        .expectStatus()
        .isBadRequest()
        .expectBody()
        .jsonPath("$.errors[0].field")
        .isEqualTo("kind");
    request(
            CUSTOMER,
            rma("order-1", "V-1", 1, "RETURN").replace("It stopped charging", "x".repeat(1001)))
        .expectStatus()
        .isBadRequest()
        .expectBody()
        .jsonPath("$.errors[0].field")
        .isEqualTo("reason");
  }

  @Test
  void onlyACustomerRequestsAnRmaAndWithAnIdempotencyKey() {
    request(STAFF, rma("order-1", "V-1", 1, "RETURN")).expectStatus().isForbidden();
    http.post()
        .uri("/rmas")
        .headers(h -> h.setBearerAuth(CUSTOMER))
        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
        .body(rma("order-1", "V-1", 1, "RETURN"))
        .exchange()
        .expectStatus()
        .isBadRequest();
  }

  @Test
  void aRepeatedRequestIsAnsweredOnceWithTheSameRma() {
    var charger = newVariant("CHARGER");
    var shipment =
        Shipments.shipment(CUSTOMER_ID).line(charger, "Charger", 1, "NONE").delivered(RECENTLY);
    var body = rma(shipment.orderId(), charger, 1, "RETURN");
    var key = java.util.UUID.randomUUID().toString();
    var first =
        await()
            .atMost(Duration.ofSeconds(20))
            .until(
                () -> requestWithKey(CUSTOMER, key, body).expectBody(RmaView.class).returnResult(),
                r -> r.getStatus().value() == 201);

    var again = requestWithKey(CUSTOMER, key, body);

    again.expectStatus().isCreated().expectHeader().valueEquals("Idempotent-Replayed", "true");
    assertThat(body(again).id()).isEqualTo(first.getResponseBody().id());
  }
}
