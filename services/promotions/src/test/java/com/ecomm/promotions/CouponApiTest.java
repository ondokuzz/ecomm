package com.ecomm.promotions;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;

/** Staff create, read, change, list and delete Coupons. */
class CouponApiTest extends PromotionsApiTest {

  @Test
  void staffCreateACouponAndReadItBack() {
    create(
            """
            {"code": "SPRING25", "discount": {"type": "AMOUNT_OFF",
             "amountOff": {"amountMinor": 2500, "currency": "EUR"}},
             "minimumSubtotal": {"amountMinor": 10000, "currency": "EUR"},
             "validFrom": "2026-03-01T00:00:00Z", "validUntil": "2026-06-01T00:00:00Z",
             "active": true}
            """)
        .expectStatus()
        .isCreated()
        .expectHeader()
        .location("/coupons/SPRING25");

    read("SPRING25")
        .expectStatus()
        .isOk()
        .expectBody()
        .json(
            """
            {"code": "SPRING25", "discount": {"type": "AMOUNT_OFF", "percentOff": null,
             "amountOff": {"amountMinor": 2500, "currency": "EUR"}},
             "minimumSubtotal": {"amountMinor": 10000, "currency": "EUR"},
             "validFrom": "2026-03-01T00:00:00Z", "validUntil": "2026-06-01T00:00:00Z",
             "active": true}
            """);
  }

  @Test
  void aPercentageCouponHasNoMinimumUnlessGivenOne() {
    create(percentOff("TENOFF", 10))
        .expectStatus()
        .isCreated()
        .expectBody()
        .json(
            """
            {"code": "TENOFF", "discount": {"type": "PERCENT_OFF", "percentOff": 10,
             "amountOff": null},
             "minimumSubtotal": null, "active": true}
            """);
  }

  @Test
  void aCodeIsStoredUpperCaseAndMatchedInAnyCase() {
    create(percentOff("summer-sale_2", 15))
        .expectStatus()
        .isCreated()
        .expectHeader()
        .location("/coupons/SUMMER-SALE_2")
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("SUMMER-SALE_2");

    read("Summer-Sale_2")
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("SUMMER-SALE_2");
  }

  @Test
  void aCodeThatIsTakenInAnyCaseIsAConflict() {
    created(percentOff("TWICE", 10));

    create(percentOff("twice", 20))
        .expectStatus()
        .isEqualTo(409)
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON);
    read("TWICE").expectBody().jsonPath("$.discount.percentOff").isEqualTo(10);
  }

  @Test
  void staffReplaceACoupon() {
    created(percentOff("CHANGEME", 10));

    update(
            "changeme",
            """
            {"discount": {"type": "AMOUNT_OFF", "amountOff": {"amountMinor": 500, "currency": "USD"}},
             "minimumSubtotal": {"amountMinor": 2000, "currency": "USD"},
             "validFrom": "2027-01-01T00:00:00Z", "validUntil": "2027-02-01T00:00:00Z",
             "active": false}
            """)
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("CHANGEME");

    read("CHANGEME")
        .expectBody()
        .json(
            """
            {"code": "CHANGEME", "discount": {"type": "AMOUNT_OFF", "percentOff": null,
             "amountOff": {"amountMinor": 500, "currency": "USD"}},
             "minimumSubtotal": {"amountMinor": 2000, "currency": "USD"},
             "validFrom": "2027-01-01T00:00:00Z", "validUntil": "2027-02-01T00:00:00Z",
             "active": false}
            """);
  }

  @Test
  void replacingWithAnotherCodeInTheBodyIsABadRequest() {
    created(percentOff("KEEPCODE", 10));

    update("KEEPCODE", percentOff("OTHERCODE", 10)).expectStatus().isBadRequest();
    update("KEEPCODE", percentOff("keepcode", 20)).expectStatus().isOk();
  }

  @Test
  void replacingAnUnknownCouponIsNotFound() {
    update("NOSUCHCOUPON", percentOff("NOSUCHCOUPON", 10))
        .expectStatus()
        .isNotFound()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON);
    read("NOSUCHCOUPON").expectStatus().isNotFound();
  }

  @Test
  void staffDeleteACoupon() {
    created(percentOff("GOODBYE", 10));

    delete("goodbye").expectStatus().isNoContent();

    read("GOODBYE").expectStatus().isNotFound();
    delete("GOODBYE")
        .expectStatus()
        .isNotFound()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON);
  }

  @Test
  void anUnknownCouponIsNotFound() {
    read("NEVERMADE")
        .expectStatus()
        .isNotFound()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON);
  }

  @Test
  void staffListEveryCouponByCode() {
    created(percentOff("LIST-B", 10));
    created(percentOff("LIST-A", 20));

    var codes =
        http
            .get()
            .uri("/coupons")
            .headers(h -> h.setBearerAuth(staffToken()))
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(new ParameterizedTypeReference<List<CouponView>>() {})
            .returnResult()
            .getResponseBody()
            .stream()
            .map(CouponView::code)
            .toList();

    assertThat(codes).contains("LIST-A", "LIST-B", "WELCOME10").isSorted();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        // code
        """
        {"discount": {"type": "PERCENT_OFF", "percentOff": 10},
         "validFrom": "2020-01-01T00:00:00Z", "validUntil": "2100-01-01T00:00:00Z", "active": true}
        """,
        """
        {"code": " ", "discount": {"type": "PERCENT_OFF", "percentOff": 10},
         "validFrom": "2020-01-01T00:00:00Z", "validUntil": "2100-01-01T00:00:00Z", "active": true}
        """,
        """
        {"code": "HAS SPACE", "discount": {"type": "PERCENT_OFF", "percentOff": 10},
         "validFrom": "2020-01-01T00:00:00Z", "validUntil": "2100-01-01T00:00:00Z", "active": true}
        """,
        """
        {"code": 10, "discount": {"type": "PERCENT_OFF", "percentOff": 10},
         "validFrom": "2020-01-01T00:00:00Z", "validUntil": "2100-01-01T00:00:00Z", "active": true}
        """,
        """
        {"code": "XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX",
         "discount": {"type": "PERCENT_OFF", "percentOff": 10},
         "validFrom": "2020-01-01T00:00:00Z", "validUntil": "2100-01-01T00:00:00Z", "active": true}
        """,
        // discount
        """
        {"code": "BAD", "validFrom": "2020-01-01T00:00:00Z", "validUntil": "2100-01-01T00:00:00Z",
         "active": true}
        """,
        """
        {"code": "BAD", "discount": {"type": "HALF_OFF"},
         "validFrom": "2020-01-01T00:00:00Z", "validUntil": "2100-01-01T00:00:00Z", "active": true}
        """,
        """
        {"code": "BAD", "discount": {"type": "PERCENT_OFF"},
         "validFrom": "2020-01-01T00:00:00Z", "validUntil": "2100-01-01T00:00:00Z", "active": true}
        """,
        """
        {"code": "BAD", "discount": {"type": "PERCENT_OFF", "percentOff": 0},
         "validFrom": "2020-01-01T00:00:00Z", "validUntil": "2100-01-01T00:00:00Z", "active": true}
        """,
        """
        {"code": "BAD", "discount": {"type": "PERCENT_OFF", "percentOff": 101},
         "validFrom": "2020-01-01T00:00:00Z", "validUntil": "2100-01-01T00:00:00Z", "active": true}
        """,
        """
        {"code": "BAD", "discount": {"type": "PERCENT_OFF", "percentOff": 12.5},
         "validFrom": "2020-01-01T00:00:00Z", "validUntil": "2100-01-01T00:00:00Z", "active": true}
        """,
        """
        {"code": "BAD", "discount": {"type": "PERCENT_OFF", "percentOff": "10"},
         "validFrom": "2020-01-01T00:00:00Z", "validUntil": "2100-01-01T00:00:00Z", "active": true}
        """,
        """
        {"code": "BAD", "discount": {"type": "AMOUNT_OFF"},
         "validFrom": "2020-01-01T00:00:00Z", "validUntil": "2100-01-01T00:00:00Z", "active": true}
        """,
        """
        {"code": "BAD", "discount": {"type": "AMOUNT_OFF", "amountOff": {"amountMinor": 0, "currency": "EUR"}},
         "validFrom": "2020-01-01T00:00:00Z", "validUntil": "2100-01-01T00:00:00Z", "active": true}
        """,
        """
        {"code": "BAD", "discount": {"type": "AMOUNT_OFF", "amountOff": {"amountMinor": 1.5, "currency": "EUR"}},
         "validFrom": "2020-01-01T00:00:00Z", "validUntil": "2100-01-01T00:00:00Z", "active": true}
        """,
        """
        {"code": "BAD", "discount": {"type": "AMOUNT_OFF", "amountOff": {"amountMinor": 500, "currency": "XYZ"}},
         "validFrom": "2020-01-01T00:00:00Z", "validUntil": "2100-01-01T00:00:00Z", "active": true}
        """,
        // minimumSubtotal
        """
        {"code": "BAD", "discount": {"type": "PERCENT_OFF", "percentOff": 10},
         "minimumSubtotal": {"amountMinor": -1, "currency": "EUR"},
         "validFrom": "2020-01-01T00:00:00Z", "validUntil": "2100-01-01T00:00:00Z", "active": true}
        """,
        """
        {"code": "BAD", "discount": {"type": "PERCENT_OFF", "percentOff": 10},
         "minimumSubtotal": {"amountMinor": 100},
         "validFrom": "2020-01-01T00:00:00Z", "validUntil": "2100-01-01T00:00:00Z", "active": true}
        """,
        // validity
        """
        {"code": "BAD", "discount": {"type": "PERCENT_OFF", "percentOff": 10},
         "validUntil": "2100-01-01T00:00:00Z", "active": true}
        """,
        """
        {"code": "BAD", "discount": {"type": "PERCENT_OFF", "percentOff": 10},
         "validFrom": "2020-01-01", "validUntil": "2100-01-01T00:00:00Z", "active": true}
        """,
        """
        {"code": "BAD", "discount": {"type": "PERCENT_OFF", "percentOff": 10},
         "validFrom": "2100-01-01T00:00:00Z", "validUntil": "2100-01-01T00:00:00Z", "active": true}
        """,
        """
        {"code": "BAD", "discount": {"type": "PERCENT_OFF", "percentOff": 10},
         "validFrom": "2100-01-01T00:00:00Z", "validUntil": "2020-01-01T00:00:00Z", "active": true}
        """,
        // active
        """
        {"code": "BAD", "discount": {"type": "PERCENT_OFF", "percentOff": 10},
         "validFrom": "2020-01-01T00:00:00Z", "validUntil": "2100-01-01T00:00:00Z"}
        """,
        """
        {"code": "BAD", "discount": {"type": "PERCENT_OFF", "percentOff": 10},
         "validFrom": "2020-01-01T00:00:00Z", "validUntil": "2100-01-01T00:00:00Z", "active": "yes"}
        """,
        "not json"
      })
  void anInvalidCouponIsABadRequest(String body) {
    create(body)
        .expectStatus()
        .isBadRequest()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON);
    read("BAD").expectStatus().isNotFound();
  }

  record CouponView(String code) {}
}
