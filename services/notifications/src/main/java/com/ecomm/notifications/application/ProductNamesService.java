package com.ecomm.notifications.application;

import com.ecomm.commons.events.Versions;
import com.ecomm.notifications.application.port.in.ProductNamesUseCase;
import com.ecomm.notifications.application.port.out.ProductNames;
import com.ecomm.notifications.application.port.out.Transactions;
import java.util.LinkedHashMap;

/**
 * Applies a Product's event if it is newer than what is held, reading and writing in one
 * transaction.
 */
public class ProductNamesService implements ProductNamesUseCase {

  private final ProductNames products;
  private final Transactions transactions;

  public ProductNamesService(ProductNames products, Transactions transactions) {
    this.products = products;
    this.transactions = transactions;
  }

  @Override
  public boolean apply(ProductPublished event) {
    var labels = new LinkedHashMap<String, String>();
    event
        .variantAxisValues()
        .forEach((variantId, axes) -> labels.put(variantId, String.join(", ", axes.values())));
    return transactions.inTransaction(
        () ->
            Versions.applyIfNewer(
                event.version(),
                products.versionOf(event.sku()),
                Long::longValue,
                () -> products.save(event.sku(), event.version(), event.name(), labels)));
  }
}
