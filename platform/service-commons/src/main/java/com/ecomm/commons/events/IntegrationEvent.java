package com.ecomm.commons.events;

/**
 * A state-carrying snapshot of one aggregate, published to its context's topic (ADR 0002). Each
 * topic carries one event type, a record whose components are the event's JSON fields: the
 * aggregate's ID, its {@link #version()}, {@code change} (why it was published, from the topic's
 * set) and the aggregate's state. {@link IntegrationEventPublisher} adds {@code eventId} and {@code
 * occurredAt}.
 *
 * <p>The topic's JSON Schema in {@code platform/event-schemas} is the event's definition: a record
 * that doesn't match it is refused when it is produced.
 */
public interface IntegrationEvent {

  /** The topic it is published to, named {@code <context>.<aggregate>}. */
  String topic();

  /** The aggregate's ID: the message key, so one aggregate's events stay on one partition. */
  String aggregateId();

  /** The aggregate's version, which goes up by at least one with every change. */
  long version();
}
