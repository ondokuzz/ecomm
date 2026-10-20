package com.ecomm.orchestration.adapter.in.kafka;

import com.ecomm.commons.web.CorrelationId;
import com.ecomm.orchestration.application.port.in.CheckoutRequest.Amount;
import com.ecomm.orchestration.application.port.in.ReturnsUseCase;
import com.ecomm.orchestration.application.port.in.ReturnsUseCase.RmaChanged;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Starts and wakes the returns Saga from Returns & Warranty's RMA events (ADR 0009). */
@Component
class RmaListener {

  /**
   * The fields of a {@code returns-warranty.rma} event the Saga needs. Its status and kind are read
   * as text, since Returns & Warranty may add one; its Refund amount and Send-back deadline are
   * absent where the RMA has none yet. An event missing a field the Saga can't do without is
   * dead-lettered at once.
   */
  record RmaEvent(
      @JsonProperty(required = true) String rmaId,
      @JsonProperty(required = true) long version,
      @JsonProperty(required = true) Rma rma) {

    record Rma(
        @JsonProperty(required = true) String reference,
        @JsonProperty(required = true) String orderId,
        @JsonProperty(required = true) String customerId,
        @JsonProperty(required = true) String variantId,
        @JsonProperty(required = true) int quantity,
        @JsonProperty(required = true) String kind,
        @JsonProperty(required = true) String status,
        Money refundAmount,
        Instant sendBackBy) {}

    record Money(long amountMinor, String currency) {}
  }

  private final ReturnsUseCase returns;

  RmaListener(ReturnsUseCase returns) {
    this.returns = returns;
  }

  /**
   * On its first start the group reads from the earliest offset, so RMAs approved before the Saga
   * existed, in Sprint 5, start theirs on upgrade; from then on it resumes where it left off.
   */
  @KafkaListener(
      topics = "returns-warranty.rma",
      groupId = "orchestration.rmas",
      properties = "auto.offset.reset=earliest")
  void on(RmaEvent event) {
    var rma = event.rma();
    var refund = rma.refundAmount();
    returns.rmaChanged(
        new RmaChanged(
            event.rmaId(),
            event.version(),
            rma.status(),
            rma.reference(),
            rma.orderId(),
            rma.customerId(),
            rma.variantId(),
            rma.quantity(),
            rma.kind(),
            refund == null ? null : new Amount(refund.amountMinor(), refund.currency()),
            rma.sendBackBy(),
            CorrelationId.current().orElse(null)));
  }
}
