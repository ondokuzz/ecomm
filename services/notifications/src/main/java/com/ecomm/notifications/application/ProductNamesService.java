package com.ecomm.notifications.application;

import com.ecomm.commons.events.Versions;
import com.ecomm.notifications.application.port.in.ProductNamesUseCase;
import com.ecomm.notifications.application.port.out.ProductNames;
import com.ecomm.notifications.application.port.out.ProductNames.VariantPrice;
import com.ecomm.notifications.application.port.out.Transactions;
import java.util.LinkedHashMap;

/**
 * Applies a Product's event if it is newer than what is held, reading and writing in one
 * transaction. A Product with a Variant projected before Prices were kept also takes the version
 * held, so a replay from the start gives it its Prices; as its first, they are no change.
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
    var variants = new LinkedHashMap<String, VariantPrice>();
    event
        .variantAxisValues()
        .forEach(
            (variantId, axes) ->
                variants.put(
                    variantId,
                    new VariantPrice(
                        String.join(", ", axes.values()), event.variantPrices().get(variantId))));
    Runnable save =
        () ->
            products.save(event.sku(), event.version(), event.name(), variants, event.occurredAt());
    return transactions.inTransaction(
        () -> {
          var held = products.versionOf(event.sku());
          if (held.isPresent()
              && held.get() == event.version()
              && products.withoutPrice(event.sku())) {
            save.run();
            return true;
          }
          return Versions.applyIfNewer(event.version(), held, Long::longValue, save);
        });
  }

  @Override
  public boolean needsReplay() {
    return products.anyWithoutPrice();
  }
}
