package com.ecomm.commons.events.outbox;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import java.util.Locale;
import org.springframework.modulith.events.EventPublication.Status;
import org.springframework.modulith.events.core.EventPublicationRepository;

/**
 * The outbox's events not yet sent, as {@code ecomm.outbox.incomplete.publications}, one gauge per
 * status: {@code published} and {@code processing} are on their way, {@code failed} and {@code
 * resubmitted} are waiting for the {@link FailedPublicationResubmitter}. Each scrape counts them in
 * the {@code event_publication} table.
 */
class IncompletePublicationsGauge implements MeterBinder {

  private final EventPublicationRepository publications;

  IncompletePublicationsGauge(EventPublicationRepository publications) {
    this.publications = publications;
  }

  @Override
  public void bindTo(MeterRegistry registry) {
    for (var status : Status.values()) {
      if (status != Status.COMPLETED) {
        Gauge.builder(
                "ecomm.outbox.incomplete.publications",
                publications,
                repository -> repository.countByStatus(status))
            .description("Events in the outbox not yet sent to Kafka")
            .tag("status", status.name().toLowerCase(Locale.ROOT))
            .register(registry);
      }
    }
  }
}
