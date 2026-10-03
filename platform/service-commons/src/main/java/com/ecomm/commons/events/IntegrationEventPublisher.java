package com.ecomm.commons.events;

/**
 * Publishes an integration event together with the change it describes. Call it inside the use
 * case's transaction: the event is recorded in that transaction and sent once it commits, so a
 * change and its event never disagree. A rolled-back transaction publishes nothing.
 *
 * <p>Delivery is at least once and not strictly in order; consumers apply events by version (see
 * {@link Versions}).
 */
public interface IntegrationEventPublisher {

  /**
   * Records {@code event} for publishing when the current transaction commits.
   *
   * @throws IllegalStateException if no transaction is active
   */
  void publish(IntegrationEvent event);
}
