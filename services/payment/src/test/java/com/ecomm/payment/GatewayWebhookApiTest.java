package com.ecomm.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.ecomm.payment.adapter.out.gateway.WebhookDelivery.SignedWebhook;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;

/**
 * A card the Customer's bank confirms later: the mock gateway answers {@code tok_async_approve} and
 * {@code tok_async_decline} with {@code PENDING}, then settles the authorization with a signed
 * webhook to {@code POST /webhooks/gateway}, recorded at most once per gateway event ID.
 */
class GatewayWebhookApiTest extends PaymentApiTest {

  @Test
  void aBankConfirmedCardIsRecordedAsPending() {
    var payment = pending("order-async-pending", "tok_async_approve");

    assertThat(payment.status()).isEqualTo("PENDING");
    assertThat(payment.declineReason()).isNull();
    assertThat(payment.gatewayReference()).isNotBlank();
    assertThat(payment.transactions())
        .singleElement()
        .satisfies(
            authorization -> {
              assertThat(authorization.kind()).isEqualTo("AUTHORIZATION");
              assertThat(authorization.outcome()).isEqualTo("PENDING");
              assertThat(authorization.gatewayEventId()).isNull();
            });
    assertThat(staffPayments("order-async-pending")).containsExactly(payment);
  }

  @Test
  void theBankApprovingSettlesThePaymentAsAuthorized() {
    var payment = pending("order-async-approved", "tok_async_approve");

    deliver(webhooks.about(payment.gatewayReference())).expectStatus().isOk();

    var settled = staffPayments("order-async-approved").getFirst();
    assertThat(settled.status()).isEqualTo("AUTHORIZED");
    assertThat(settled.declineReason()).isNull();
    assertThat(settled.gatewayReference()).isEqualTo(payment.gatewayReference());
    assertThat(settled.transactions())
        .extracting(TransactionView::kind, TransactionView::outcome)
        .containsExactly(tuple("AUTHORIZATION", "PENDING"), tuple("AUTHORIZATION", "APPROVED"));
    var settlement = settled.transactions().getLast();
    assertThat(settlement.gatewayEventId()).isNotBlank();
    assertThat(settlement.gatewayReference()).isEqualTo(payment.gatewayReference());
    assertThat(settlement.amount()).isEqualTo(payment.amount());
    assertThat(settlement.backfilled()).isFalse();
  }

  @Test
  void theBankDecliningSettlesThePaymentAsDeclinedWithItsReason() {
    var payment = pending("order-async-declined", "tok_async_decline");

    deliver(webhooks.about(payment.gatewayReference())).expectStatus().isOk();

    var settled = staffPayments("order-async-declined").getFirst();
    assertThat(settled.status()).isEqualTo("DECLINED");
    assertThat(settled.declineReason()).isEqualTo("card_declined");
    assertThat(settled.transactions())
        .extracting(TransactionView::outcome, TransactionView::declineReason)
        .containsExactly(tuple("PENDING", null), tuple("DECLINED", "card_declined"));
  }

  @Test
  void aSettlementPublishesThePaymentWithTheWebhooksEventId() {
    var payment = pending("order-async-events", "tok_async_decline");
    deliver(webhooks.about(payment.gatewayReference())).expectStatus().isOk();

    var records = PaymentEvents.keyed(payment.id(), 2);

    assertThat(records).allSatisfy(r -> assertThat(PaymentEvents.schemaViolationsOf(r)).isEmpty());
    var events = records.stream().map(PaymentEvents::valueOf).toList();
    assertThat(events)
        .extracting(e -> e.get("change").asText())
        .containsExactly("PENDING", "DECLINED");
    assertThat(events).extracting(e -> e.get("version").asLong()).containsExactly(1L, 2L);
    var settled = events.getLast().get("payment");
    assertThat(settled.get("declineReason").asText()).isEqualTo("card_declined");
    assertThat(settled.at("/transactions/1/gatewayEventId").asText())
        .isEqualTo(
            staffPayments("order-async-events").getFirst().transactions().get(1).gatewayEventId());
  }

  @Test
  void aWebhookDeliveredTwiceIsRecordedOnce() {
    var payment = pending("order-async-twice", "tok_async_approve");
    var webhook = webhooks.about(payment.gatewayReference());

    deliver(webhook).expectStatus().isOk();
    var settled = staffPayments("order-async-twice");
    deliver(webhook).expectStatus().isOk();

    assertThat(staffPayments("order-async-twice")).isEqualTo(settled);
    assertThat(settled.getFirst().transactions()).hasSize(2);
    assertThat(PaymentEvents.keyed(payment.id(), 2)).hasSize(2);
  }

  @Test
  void aSecondSettlementOfASettledPaymentChangesNothing() {
    var payment = pending("order-async-reordered", "tok_async_approve");
    deliver(webhooks.about(payment.gatewayReference())).expectStatus().isOk();
    var settled = staffPayments("order-async-reordered");

    deliver(
            signed(
                """
                {"eventId": "evt-late-decline-%s", "reference": "%s", "outcome": "DECLINED",
                 "declineReason": "card_declined"}
                """
                    .formatted(payment.id(), payment.gatewayReference())))
        .expectStatus()
        .isOk();

    assertThat(staffPayments("order-async-reordered")).isEqualTo(settled);
    assertThat(settled.getFirst().status()).isEqualTo("AUTHORIZED");
  }

  @Test
  void webhooksOutOfOrderAreSettledByTheFirstToArrive() {
    var payment = pending("order-async-out-of-order", "tok_async_approve");
    var approval = webhooks.about(payment.gatewayReference());
    var decline =
        signed(
            """
            {"eventId": "evt-early-decline-%s", "reference": "%s", "outcome": "DECLINED",
             "declineReason": "card_declined"}
            """
                .formatted(payment.id(), payment.gatewayReference()));

    deliver(decline).expectStatus().isOk();
    deliver(approval).expectStatus().isOk();

    var settled = staffPayments("order-async-out-of-order").getFirst();
    assertThat(settled.status()).isEqualTo("DECLINED");
    assertThat(settled.transactions())
        .extracting(TransactionView::outcome, TransactionView::gatewayEventId)
        .containsExactly(
            tuple("PENDING", null), tuple("DECLINED", "evt-early-decline-" + payment.id()));
  }

  @Test
  void aPendingPaymentCanBeVoidedAndItsLateSettlementChangesNothing() {
    var payment = pending("order-async-void", "tok_async_approve");

    var voided =
        voidPayment(payment.id(), "customer-42")
            .expectStatus()
            .isOk()
            .expectBody(PaymentView.class)
            .returnResult()
            .getResponseBody();
    deliver(webhooks.about(payment.gatewayReference())).expectStatus().isOk();

    assertThat(voided.status()).isEqualTo("VOIDED");
    assertThat(voided.transactions())
        .extracting(TransactionView::kind, TransactionView::outcome)
        .containsExactly(tuple("AUTHORIZATION", "PENDING"), tuple("VOID", "APPROVED"));
    assertThat(staffPayments("order-async-void")).containsExactly(voided);
  }

  @Test
  void aWebhookForAnUnknownReferenceIsNotFound() {
    deliver(
            signed(
                """
                {"eventId": "evt-unknown-1", "reference": "mock-unknown", "outcome": "APPROVED"}
                """))
        .expectStatus()
        .isNotFound();
  }

  @Test
  void aWebhookForAnUnknownReferenceIsNotRecordedSoItCanBeDeliveredLater() {
    var payment = pending("order-async-later", "tok_async_approve");
    var webhook = webhooks.about(payment.gatewayReference());
    var unknown = signed(webhook.body().replace(payment.gatewayReference(), "mock-not-yet-known"));
    deliver(unknown).expectStatus().isNotFound();

    deliver(webhook).expectStatus().isOk();

    assertThat(staffPayments("order-async-later").getFirst().status()).isEqualTo("AUTHORIZED");
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "sha256=", "sha256=00", "deadbeef"})
  void aWebhookWithAWrongSignatureIsUnauthorizedAndChangesNothing(String signature) {
    var payment = pending("order-async-forged", "tok_async_approve");
    var webhook = webhooks.about(payment.gatewayReference());

    deliver(new SignedWebhook(webhook.body(), signature))
        .expectStatus()
        .isUnauthorized()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON);

    assertThat(staffPayments("order-async-forged").getFirst().status()).isEqualTo("PENDING");
  }

  @Test
  void aWebhookSignedForAnotherBodyIsUnauthorized() {
    var payment = pending("order-async-tampered", "tok_async_approve");
    var webhook = webhooks.about(payment.gatewayReference());

    deliver(new SignedWebhook(webhook.body().replace("APPROVED", "DECLINED"), webhook.signature()))
        .expectStatus()
        .isUnauthorized();

    assertThat(staffPayments("order-async-tampered").getFirst().status()).isEqualTo("PENDING");
  }

  @Test
  void aWebhookWithoutASignatureIsUnauthorized() {
    var payment = pending("order-async-unsigned", "tok_async_approve");

    http.post()
        .uri("/webhooks/gateway")
        .contentType(MediaType.APPLICATION_JSON)
        .body(webhooks.about(payment.gatewayReference()).body())
        .exchange()
        .expectStatus()
        .isUnauthorized();

    assertThat(staffPayments("order-async-unsigned").getFirst().status()).isEqualTo("PENDING");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "not json",
        "{}",
        "{\"eventId\": \"evt-bad-1\", \"reference\": \"mock-x\", \"outcome\": \"PENDING\"}",
        "{\"eventId\": \"evt-bad-2\", \"reference\": \"mock-x\", \"outcome\": \"DECLINED\"}",
        "{\"eventId\": 7, \"reference\": \"mock-x\", \"outcome\": \"APPROVED\"}",
        "{\"reference\": \"mock-x\", \"outcome\": \"APPROVED\"}"
      })
  void aMalformedSignedWebhookIsABadRequest(String body) {
    deliver(signed(body)).expectStatus().isBadRequest();
  }

  /** {@code body} signed as the gateway signs it, with the secret it shares with Payment. */
  static SignedWebhook signed(String body) {
    try {
      var mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(WEBHOOK_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      return new SignedWebhook(
          body,
          "sha256=" + HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8))));
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException(e);
    }
  }

  /** A Payment of {@code orderId} paid with a bank-confirmed card, as it was first recorded. */
  PaymentView pending(String orderId, String paymentMethod) {
    return authorize(orderId, paymentMethod)
        .expectStatus()
        .isCreated()
        .expectBody(PaymentView.class)
        .returnResult()
        .getResponseBody();
  }
}
