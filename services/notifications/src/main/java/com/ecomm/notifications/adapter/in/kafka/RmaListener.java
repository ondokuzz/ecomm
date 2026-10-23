package com.ecomm.notifications.adapter.in.kafka;

import com.ecomm.notifications.application.port.in.RmaEventsUseCase;
import com.ecomm.notifications.application.port.in.RmaEventsUseCase.RmaChanged;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Returns & Warranty's RMA events, which tell Customers each step of their RMAs. */
@Component
class RmaListener {

  private static final Logger log = LoggerFactory.getLogger(RmaListener.class);

  /** The fields of a {@code returns-warranty.rma} event Notifications needs. */
  record RmaEvent(
      @JsonProperty(required = true) String eventId,
      @JsonProperty(required = true) Instant occurredAt,
      @JsonProperty(required = true) String rmaId,
      @JsonProperty(required = true) String change,
      @JsonProperty(required = true) Rma rma) {
    record Rma(
        @JsonProperty(required = true) String orderId,
        @JsonProperty(required = true) String customerId,
        @JsonProperty(required = true) String kind,
        @JsonProperty(required = true) String productName,
        @JsonProperty(required = true) int quantity,
        Instant sendBackBy,
        String warehouseId,
        Inspection inspection,
        @JsonProperty(required = true) List<Decision> decisions) {}

    record Inspection(String note) {}

    record Decision(@JsonProperty(required = true) String status, String note) {}
  }

  private final RmaEventsUseCase rmas;

  RmaListener(RmaEventsUseCase rmas) {
    this.rmas = rmas;
  }

  /**
   * On its first start the group reads from the latest offset, so no RMA step from before
   * Notifications existed is announced; from then on it resumes where it left off.
   */
  @KafkaListener(
      topics = "returns-warranty.rma",
      groupId = "notifications.rmas",
      properties = "auto.offset.reset=latest")
  void on(RmaEvent event) {
    var rma = event.rma();
    var recorded =
        rmas.rmaChanged(
            new RmaChanged(
                event.eventId(),
                event.occurredAt(),
                event.change(),
                event.rmaId(),
                rma.orderId(),
                rma.customerId(),
                rma.kind(),
                rma.productName(),
                rma.quantity(),
                rma.sendBackBy(),
                rma.warehouseId(),
                rejectionReasonOf(rma),
                rma.inspection() == null ? null : rma.inspection().note()));
    if (!recorded.isEmpty()) {
      log.info("Recorded {} for RMA {} from event {}", recorded, event.rmaId(), event.eventId());
    }
  }

  /** The rejection's note: its reason, in the RMA history. */
  private static String rejectionReasonOf(RmaEvent.Rma rma) {
    return rma.decisions().stream()
        .filter(d -> "REJECTED".equals(d.status()))
        .map(RmaEvent.Decision::note)
        .reduce((first, last) -> last)
        .orElse(null);
  }
}
