package com.ecomm.notifications.adapter.in.kafka;

import com.ecomm.commons.money.Money;
import com.ecomm.notifications.application.port.in.PaymentEventsUseCase;
import com.ecomm.notifications.application.port.in.PaymentEventsUseCase.PaymentChanged;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Payment's events, which tell Customers they were refunded. */
@Component
class PaymentListener {

  private static final Logger log = LoggerFactory.getLogger(PaymentListener.class);

  /** The fields of a {@code payment.payment} event Notifications needs. */
  record PaymentEvent(
      @JsonProperty(required = true) String eventId,
      @JsonProperty(required = true) String change,
      @JsonProperty(required = true) Payment payment) {
    record Payment(
        @JsonProperty(required = true) String orderId,
        @JsonProperty(required = true) String customerId,
        @JsonProperty(required = true) List<Transaction> transactions) {}

    record Transaction(
        @JsonProperty(required = true) String kind,
        @JsonProperty(required = true) String outcome,
        @JsonProperty(required = true) MoneyJson amount,
        @JsonProperty(required = true) Instant at,
        String reference) {}

    record MoneyJson(
        @JsonProperty(required = true) long amountMinor,
        @JsonProperty(required = true) String currency) {}
  }

  private final PaymentEventsUseCase payments;

  PaymentListener(PaymentEventsUseCase payments) {
    this.payments = payments;
  }

  /**
   * On its first start the group reads from the latest offset, so no Refund from before
   * Notifications existed is announced; from then on it resumes where it left off.
   */
  @KafkaListener(
      topics = "payment.payment",
      groupId = "notifications.payments",
      properties = "auto.offset.reset=latest")
  void on(PaymentEvent event) {
    var payment = event.payment();
    var recorded =
        payments.paymentChanged(
            new PaymentChanged(
                event.eventId(),
                event.change(),
                payment.orderId(),
                payment.customerId(),
                payment.transactions().stream()
                    .map(
                        t ->
                            new PaymentChanged.Transaction(
                                t.kind(),
                                t.outcome(),
                                Money.of(t.amount().amountMinor(), t.amount().currency()),
                                t.reference(),
                                t.at()))
                    .toList()));
    if (!recorded.isEmpty()) {
      log.info(
          "Recorded {} for Order {} from event {}", recorded, payment.orderId(), event.eventId());
    }
  }
}
