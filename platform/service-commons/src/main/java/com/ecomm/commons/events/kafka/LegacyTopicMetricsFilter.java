package com.ecomm.commons.events.kafka;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.config.MeterFilterReply;

/**
 * Drops the copies of the Kafka consumer's per-topic metrics that Kafka still reports under the
 * topic's legacy name, its dots turned to underscores ({@code catalog_product} beside {@code
 * catalog.product}), so a sum over topics doesn't count everything twice. Every topic here has a
 * dot in its name, so a topic tag with an underscore and no dot is always such a copy.
 */
class LegacyTopicMetricsFilter implements MeterFilter {

  @Override
  public MeterFilterReply accept(Meter.Id id) {
    var topic = id.getName().startsWith("kafka.consumer.") ? id.getTag("topic") : null;
    return topic != null && topic.contains("_") && !topic.contains(".")
        ? MeterFilterReply.DENY
        : MeterFilterReply.NEUTRAL;
  }
}
