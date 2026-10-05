package com.ecomm.catalog.application.port.out;

import com.ecomm.catalog.domain.AttributeDefinition;
import com.ecomm.catalog.domain.Category;
import com.ecomm.commons.events.IntegrationEvent;
import java.util.List;

/**
 * The {@code catalog.category} event: a snapshot of a Category with its attribute definitions,
 * published whenever it is created, changed or removed, and once for every Category stored before
 * Category events. A removed Category's last event is its last state, marked {@code removed}. Its
 * shape is defined by {@code platform/event-schemas/schemas/catalog.category.json}.
 */
public record CategoryEvent(String slug, long version, Change change, Snapshot category)
    implements IntegrationEvent {

  public static final String TOPIC = "catalog.category";

  /** Why the event was published. */
  public enum Change {
    CREATED,
    UPDATED,
    REMOVED,
    BACKFILLED
  }

  public record Snapshot(String name, List<Definition> attributes, boolean removed) {}

  /** {@code values} are an {@code ENUM}'s allowed values, and empty for any other type. */
  public record Definition(
      String name, String type, List<String> values, boolean required, boolean variantAxis) {}

  public static CategoryEvent of(Category category, long version, Change change) {
    return new CategoryEvent(
        category.slug(),
        version,
        change,
        new Snapshot(
            category.name(),
            category.attributes().stream().map(CategoryEvent::definitionOf).toList(),
            change == Change.REMOVED));
  }

  private static Definition definitionOf(AttributeDefinition definition) {
    return new Definition(
        definition.name(),
        definition.type().name(),
        definition.values(),
        definition.required(),
        definition.variantAxis());
  }

  @Override
  public String topic() {
    return TOPIC;
  }

  @Override
  public String aggregateId() {
    return slug;
  }
}
