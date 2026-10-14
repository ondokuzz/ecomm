package com.ecomm.fulfillmentshipping.application;

import com.ecomm.commons.events.Versions;
import com.ecomm.fulfillmentshipping.application.port.in.ProjectProductUseCase;
import com.ecomm.fulfillmentshipping.application.port.out.ProductStore;
import com.ecomm.fulfillmentshipping.application.port.out.Transactions;
import com.ecomm.fulfillmentshipping.domain.Product;

/** Keeps each Product if it is newer than what is held, reading and writing in one transaction. */
public class ProductProjectionService implements ProjectProductUseCase {

  private final ProductStore products;
  private final Transactions transactions;

  public ProductProjectionService(ProductStore products, Transactions transactions) {
    this.products = products;
    this.transactions = transactions;
  }

  @Override
  public boolean apply(Product product) {
    return transactions.inTransaction(
        () ->
            Versions.applyIfNewer(
                product.version(),
                products.versionOf(product.sku()),
                Long::longValue,
                () -> products.save(product)));
  }
}
