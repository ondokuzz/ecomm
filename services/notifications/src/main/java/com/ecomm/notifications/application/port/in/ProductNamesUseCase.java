package com.ecomm.notifications.application.port.in;

import java.util.Map;

/** Keeps the newest name of each Product, for naming Order Lines. */
public interface ProductNamesUseCase {

  /** A Product's event: its name, and each Variant's axis values in their order. */
  record ProductPublished(
      String sku, long version, String name, Map<String, Map<String, String>> variantAxisValues) {}

  /** Whether it was newer than what is held, and so applied. */
  boolean apply(ProductPublished event);
}
