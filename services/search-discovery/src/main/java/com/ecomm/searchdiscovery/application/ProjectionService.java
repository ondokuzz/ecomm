package com.ecomm.searchdiscovery.application;

import com.ecomm.commons.events.Versions;
import com.ecomm.searchdiscovery.application.port.in.ProjectCatalogUseCase;
import com.ecomm.searchdiscovery.application.port.out.CategoryStore;
import com.ecomm.searchdiscovery.application.port.out.ProductStore;
import com.ecomm.searchdiscovery.application.port.out.StockStore;
import com.ecomm.searchdiscovery.domain.SearchCategory;
import com.ecomm.searchdiscovery.domain.SearchableProduct;
import com.ecomm.searchdiscovery.domain.VariantStock;

/**
 * Applies each event if it is newer than what is held. One aggregate's events arrive on one
 * partition, which one consumer reads in order, so reading then writing its one document needs no
 * transaction.
 */
public class ProjectionService implements ProjectCatalogUseCase {

  private final ProductStore products;
  private final CategoryStore categories;
  private final StockStore stock;

  public ProjectionService(ProductStore products, CategoryStore categories, StockStore stock) {
    this.products = products;
    this.categories = categories;
    this.stock = stock;
  }

  /**
   * A Product keeps the time it was listed while it stays listed; one created, or created again
   * after its removal, is listed when its event was published.
   */
  @Override
  public boolean apply(ProductPublished event) {
    var stored = products.find(event.sku());
    return Versions.applyIfNewer(
        event.version(),
        stored,
        SearchableProduct::version,
        () ->
            products.save(
                new SearchableProduct(
                    event.sku(),
                    event.version(),
                    event.name(),
                    event.description(),
                    event.category(),
                    event.attributes(),
                    event.images(),
                    event.variants(),
                    event.removed(),
                    stored
                        .filter(p -> !p.removed())
                        .map(SearchableProduct::listedAt)
                        .orElse(event.occurredAt()))));
  }

  @Override
  public boolean apply(CategoryPublished event) {
    return Versions.applyIfNewer(
        event.version(),
        categories.find(event.slug()),
        SearchCategory::version,
        () ->
            categories.save(
                new SearchCategory(
                    event.slug(),
                    event.version(),
                    event.name(),
                    event.attributes(),
                    event.removed())));
  }

  @Override
  public boolean apply(StockPublished event) {
    return Versions.applyIfNewer(
        event.version(),
        stock.find(event.variantId()),
        VariantStock::version,
        () ->
            stock.save(
                new VariantStock(
                    event.variantId(), event.version(), event.available(), event.stocked())));
  }
}
