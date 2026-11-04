package com.ecomm.supportassistant;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.notFound;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.serviceUnavailable;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Asked about a return or a warranty claim, the mock provider answers from Returns & Warranty with
 * the Customer's own token: an RMA named by its RMA reference, with its status and next step, the
 * Customer's RMAs, and whether an Order's units can still be returned or claimed under warranty,
 * until when, pointing to the Order's page to request one.
 */
class ReturnsApiTest extends SupportAssistantApiTest {

  private void stubRmaByReference(String... history) {
    var rma = rma(RMA_ID, "RETURN", history);
    stubRmasWhere("reference", "3F2A9C1B", rma);
    stubRma(RMA_ID, okJson(rma));
  }

  @Test
  void anApprovedRmaNamedByItsReferenceSaysWhereToSendItAndByWhen() {
    stubRmaByReference("REQUESTED", "APPROVED");
    stubReturnAddress();
    var id = started();

    send(id, "What's happening with RMA-3F2A9C1B?")
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.text")
        .isEqualTo(
            "Your RMA RMA-3F2A9C1B, a return of 1 Pixel 9 on Order #7F1C2A3B, is approved. Please"
                + " send it back by 21 November 2026, with RMA-3F2A9C1B on the parcel, to Ecomm"
                + " Returns Berlin, Lagerweg 3, 12099 Berlin, DE.")
        .jsonPath("$.references[0].kind")
        .isEqualTo("RMA")
        .jsonPath("$.references[0].id")
        .isEqualTo(RMA_ID)
        .jsonPath("$.references[0].label")
        .isEqualTo("RMA-3F2A9C1B")
        .jsonPath("$.references[1].kind")
        .isEqualTo("ORDER");

    RETURNS_WARRANTY.verify(
        getRequestedFor(urlPathEqualTo("/rmas"))
            .withQueryParam("reference", equalTo("3F2A9C1B"))
            .withHeader("Authorization", equalTo("Bearer " + customerToken()))
            .withHeader("X-Correlation-Id", equalTo("test-correlation-1")));
  }

  @Test
  void anApprovedRmaWhoseReturnAddressCantBeReadPointsToItsPage() {
    stubRmaByReference("REQUESTED", "APPROVED");
    var id = started();

    send(id, "What's happening with rma-3f2a9c1b?")
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.text")
        .isEqualTo(
            "Your RMA RMA-3F2A9C1B, a return of 1 Pixel 9 on Order #7F1C2A3B, is approved. Please"
                + " send it back by 21 November 2026, with RMA-3F2A9C1B on the parcel, to the"
                + " Return address on its page.");
  }

  static Stream<Arguments> statuses() {
    var rma = "Your RMA RMA-3F2A9C1B, a return of 1 Pixel 9 on Order #7F1C2A3B, ";
    return Stream.of(
        Arguments.of(
            new String[] {"REQUESTED"}, rma + "is requested, and waits for Staff to decide on it."),
        Arguments.of(
            new String[] {"REQUESTED", "REJECTED:Bought more than 30 days ago"},
            rma + "was rejected on 8 November 2026: Bought more than 30 days ago."),
        Arguments.of(
            new String[] {"REQUESTED", "APPROVED", "RECEIVED"},
            rma + "was received on 9 November 2026, and is inspected next."),
        Arguments.of(
            new String[] {"REQUESTED", "APPROVED", "EXPIRED"},
            rma
                + "expired on 9 November 2026: its Send-back deadline, 21 November 2026, passed"
                + " before it reached us."),
        Arguments.of(
            new String[] {"REQUESTED", "APPROVED", "RECEIVED", "INSPECTED"},
            rma + "was inspected on 10 November 2026 (accepted), and is resolved next."),
        Arguments.of(
            new String[] {"REQUESTED", "APPROVED", "RECEIVED", "INSPECTED", "REFUNDED"},
            rma
                + "was refunded on 11 November 2026: 799.00 EUR. Your bank may take a few days to"
                + " show it."),
        Arguments.of(
            new String[] {"REQUESTED", "APPROVED", "RECEIVED", "INSPECTED", "EXCHANGED"},
            rma
                + "was exchanged on 11 November 2026: a replacement is on its way, and its page"
                + " tracks it."),
        Arguments.of(
            new String[] {
              "REQUESTED", "APPROVED", "RECEIVED", "INSPECTED", "CLOSED:The screen is cracked"
            },
            rma + "was closed on 11 November 2026: The screen is cracked."),
        Arguments.of(
            new String[] {
              "REQUESTED", "APPROVED", "RECEIVED", "INSPECTED", "ON_HOLD:The Refund was declined"
            },
            rma
                + "is on hold since 11 November 2026: The Refund was declined. Staff will look at"
                + " it."));
  }

  @ParameterizedTest
  @MethodSource("statuses")
  void anRmasStatusIsExplainedWithItsNextStep(String[] history, String reply) {
    stubRmaByReference(history);
    var id = started();

    send(id, "Where is my return RMA-3F2A9C1B?")
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.text")
        .isEqualTo(reply)
        .jsonPath("$.references[0].id")
        .isEqualTo(RMA_ID);
  }

  @Test
  void aWarrantyClaimIsCalledOne() {
    var claim = rma(RMA_ID, "WARRANTY_CLAIM", "REQUESTED");
    stubRmasWhere("reference", "3F2A9C1B", claim);
    stubRma(RMA_ID, okJson(claim));
    var id = started();

    send(id, "RMA-3F2A9C1B?")
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.text")
        .isEqualTo(
            "Your RMA RMA-3F2A9C1B, a warranty claim of 1 Pixel 9 on Order #7F1C2A3B, is"
                + " requested, and waits for Staff to decide on it.");
  }

  @Test
  void anRmaReferenceNoneOfTheirRmasHasIsNotFoundAmongTheirs() {
    stubRmasWhere("reference", "DEADBEEF");
    var id = started();

    send(id, "Where is RMA-DEADBEEF?")
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.text")
        .isEqualTo(
            "I couldn't find an RMA RMA-DEADBEEF among yours. Please check the reference in your"
                + " email.")
        .jsonPath("$.references.length()")
        .isEqualTo(0);
  }

  @Test
  void returnsAndWarrantyFailingIsSaidSo() {
    stubRmas(serviceUnavailable());
    var id = started();

    send(id, "Where is RMA-3F2A9C1B?")
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.text")
        .isEqualTo("I couldn't look up your returns just now. Please try again in a moment.");
  }

  // --- Eligibility ---

  private void stubOrderByReference(String status) {
    var order = order(ORDER_ID, status, "PLACED", "PAID", "FULFILLED", "SHIPPED", status);
    stubOrdersByReference("7F1C2A3B", order);
    stubOrders(order);
  }

  @Test
  void whetherAnOrdersUnitsCanStillBeReturnedOrClaimedIsToldUntilWhenWithItsPage() {
    stubOrderByReference("DELIVERED");
    stubEligibility(
        ORDER_ID,
        eligibleLine("Pixel 9", "2099-12-06T09:00:00Z", "2101-11-06T09:00:00Z", 1),
        eligibleLine("USB-C Cable", "2026-11-01T09:00:00Z", null, 2),
        eligibleLine("Pixel Case", "2099-12-06T09:00:00Z", null, 0));
    var id = started();

    send(id, "Can I still return something from order #7F1C2A3B?")
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.text")
        .isEqualTo(
            "On Order #7F1C2A3B, you can return the Pixel 9 until 6 December 2099, and claim under"
                + " its warranty until 6 November 2101; the USB-C Cable's Return Period ended on 1"
                + " November 2026, and it has no warranty; every Pixel Case is already in an RMA."
                + " To request a return or a warranty claim, open Order #7F1C2A3B's page.")
        .jsonPath("$.references[0].id")
        .isEqualTo(ORDER_ID);

    RETURNS_WARRANTY.verify(
        getRequestedFor(urlPathEqualTo("/rmas/eligibility"))
            .withQueryParam("orderId", equalTo(ORDER_ID))
            .withHeader("Authorization", equalTo("Bearer " + customerToken())));
  }

  @Test
  void aWarrantyQuestionIsAnsweredForTheLatestOrder() {
    stubOrderByReference("DELIVERED");
    stubEligibility(
        ORDER_ID, eligibleLine("Pixel 9", "2026-11-01T09:00:00Z", "2101-11-06T09:00:00Z", 1));
    var id = started();

    send(id, "Is my phone still under warranty?")
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.text")
        .isEqualTo(
            "On your latest Order, #7F1C2A3B, the Pixel 9's Return Period ended on 1 November"
                + " 2026, but you can claim under its warranty until 6 November 2101. To request a"
                + " return or a warranty claim, open Order #7F1C2A3B's page.");
  }

  @Test
  void anOrderPastEveryWindowHasNothingToRequest() {
    stubOrderByReference("DELIVERED");
    stubEligibility(
        ORDER_ID, eligibleLine("Pixel 9", "2026-10-01T09:00:00Z", "2026-11-01T09:00:00Z", 1));
    var id = started();

    send(id, "Can I return order #7F1C2A3B?")
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.text")
        .isEqualTo(
            "On Order #7F1C2A3B, the Pixel 9's Return Period ended on 1 October 2026, and its"
                + " Warranty Window on 1 November 2026.");
  }

  @Test
  void anOrderNotYetDeliveredCanBeReturnedOnceItIs() {
    stubOrderByReference("SHIPPED");
    stubEligibility(ORDER_ID, notFound());
    var id = started();

    send(id, "Can I return order #7F1C2A3B?")
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.text")
        .isEqualTo(
            "Order #7F1C2A3B isn't delivered yet, so nothing on it can be returned yet. Once it"
                + " is, you can request a return on its page.");
  }

  // --- The Customer's RMAs ---

  @Test
  void anOrdersRmasAreListed() {
    stubOrderByReference("DELIVERED");
    stubRmasWhere(
        "orderId",
        ORDER_ID,
        rma(OTHER_RMA_ID, "WARRANTY_CLAIM", "REQUESTED", "APPROVED"),
        rma(RMA_ID, "RETURN", "REQUESTED", "APPROVED", "RECEIVED", "INSPECTED", "REFUNDED"));
    var id = started();

    send(id, "Which returns do I have on order #7F1C2A3B?")
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.text")
        .isEqualTo(
            "You have 2 RMAs on Order #7F1C2A3B: RMA-A9B8C7D6, a warranty claim of 1 Pixel 9,"
                + " approved; and RMA-3F2A9C1B, a return of 1 Pixel 9, refunded.")
        .jsonPath("$.references[*].reference")
        .isEqualTo(List.of("#7F1C2A3B", "RMA-A9B8C7D6", "RMA-3F2A9C1B"));
  }

  @Test
  void theCustomersOnlyRmaIsExplained() {
    stubRmas(rma(RMA_ID, "RETURN", "REQUESTED"));
    stubRma(RMA_ID, okJson(rma(RMA_ID, "RETURN", "REQUESTED")));
    var id = started();

    send(id, "Where is my return?")
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.text")
        .isEqualTo(
            "Your RMA RMA-3F2A9C1B, a return of 1 Pixel 9 on Order #7F1C2A3B, is requested, and"
                + " waits for Staff to decide on it.");
  }

  @Test
  void whereAWarrantyClaimIsIsAnsweredFromTheCustomersRmas() {
    var claim = rma(RMA_ID, "WARRANTY_CLAIM", "REQUESTED", "APPROVED", "RECEIVED");
    stubRmas(claim);
    stubRma(RMA_ID, okJson(claim));
    var id = started();

    send(id, "Where is my warranty claim?")
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.text")
        .isEqualTo(
            "Your RMA RMA-3F2A9C1B, a warranty claim of 1 Pixel 9 on Order #7F1C2A3B, was received"
                + " on 9 November 2026, and is inspected next.");
  }

  @Test
  void aClosedRmaGivesItsClosingNoteOverItsInspections() {
    var closed =
        rma(RMA_ID, "RETURN", "REQUESTED", "APPROVED", "RECEIVED", "INSPECTED", "CLOSED:Kept by us")
            .replace(
                "\"note\": \"Kept by us\"}, \"resolution\"",
                "\"note\": \"Water damage\"}, \"resolution\"");
    stubRmasWhere("reference", "3F2A9C1B", closed);
    stubRma(RMA_ID, okJson(closed));
    var id = started();

    send(id, "RMA-3F2A9C1B?")
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.text")
        .value(
            String.class,
            text -> assertThat(text).endsWith("was closed on 11 November 2026: Kept by us."));
  }

  @Test
  void aCustomerWithNoRmasIsToldWhereToRequestOne() {
    stubRmas();
    var id = started();

    send(id, "Where is my return?")
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.text")
        .isEqualTo("You have no RMAs. To request a return, open the Order's page under My Orders.");
  }
}
