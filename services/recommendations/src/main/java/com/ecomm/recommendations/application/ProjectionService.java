package com.ecomm.recommendations.application;

import com.ecomm.commons.events.Versions;
import com.ecomm.recommendations.application.port.in.ProjectionUseCase;
import com.ecomm.recommendations.application.port.out.CategoryStore;
import com.ecomm.recommendations.application.port.out.OrderStore;
import com.ecomm.recommendations.application.port.out.ProductStore;
import com.ecomm.recommendations.application.port.out.StockStore;
import com.ecomm.recommendations.application.port.out.Transactions;
import com.ecomm.recommendations.domain.Category;
import com.ecomm.recommendations.domain.Order;
import com.ecomm.recommendations.domain.Product;
import com.ecomm.recommendations.domain.VariantStock;
import java.util.HashSet;

/** Applies each event if it is newer than what is held, reading and writing in one transaction. */
public class ProjectionService implements ProjectionUseCase {

  private final OrderStore orders;
  private final ProductStore products;
  private final CategoryStore categories;
  private final StockStore stock;
  private final Transactions transactions;

  public ProjectionService(
      OrderStore orders,
      ProductStore products,
      CategoryStore categories,
      StockStore stock,
      Transactions transactions) {
    this.orders = orders;
    this.products = products;
    this.categories = categories;
    this.stock = stock;
    this.transactions = transactions;
  }

  @Override
  public boolean apply(OrderPublished event) {
    return transactions.inTransaction(
        () ->
            Versions.applyIfNewer(
                event.version(),
                orders.versionOf(event.orderId()),
                Long::longValue,
                () ->
                    orders.save(
                        new Order(
                            event.orderId(),
                            event.version(),
                            event.status().counts(),
                            event.placedAt(),
                            new HashSet<>(event.variantIds())))));
  }

  /**
   * A Product keeps every Variant it has ever had, so an Order for a Variant Catalog has since
   * dropped still counts as buying its Product. One kept without a Category, before Sprint 6, takes
   * the event at its own version too, as if it were newer.
   */
  @Override
  public boolean apply(ProductPublished event) {
    return transactions.inTransaction(
        () ->
            Versions.applyIfNewer(
                event.version(),
                products.held(event.sku()),
                ProductStore.Held::versionToBeat,
                () ->
                    products.save(
                        new Product(
                            event.sku(),
                            event.version(),
                            event.removed(),
                            event.category(),
                            new HashSet<>(event.variantIds())))));
  }

  @Override
  public boolean apply(CategoryPublished event) {
    return transactions.inTransaction(
        () ->
            Versions.applyIfNewer(
                event.version(),
                categories.versionOf(event.slug()),
                Long::longValue,
                () ->
                    categories.save(
                        new Category(event.slug(), event.name()),
                        event.version(),
                        event.removed())));
  }

  @Override
  public boolean apply(StockPublished event) {
    return transactions.inTransaction(
        () ->
            Versions.applyIfNewer(
                event.version(),
                stock.versionOf(event.variantId()),
                Long::longValue,
                () ->
                    stock.save(
                        VariantStock.of(
                            event.variantId(),
                            event.version(),
                            event.stocked(),
                            event.available()))));
  }

  @Override
  public boolean productsNeedReplay() {
    return products.anyWithoutCategory();
  }
}
