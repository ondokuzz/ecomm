package com.ecomm.catalog.application;

import com.ecomm.catalog.application.port.in.BrowseCatalogUseCase;
import com.ecomm.catalog.application.port.in.ManageCategoriesUseCase;
import com.ecomm.catalog.application.port.in.ManageProductsUseCase;
import com.ecomm.catalog.application.port.in.PublishBackfillUseCase;
import com.ecomm.catalog.application.port.in.SeedCatalogUseCase;
import com.ecomm.catalog.application.port.out.CategoryEvent;
import com.ecomm.catalog.application.port.out.CategoryRepository;
import com.ecomm.catalog.application.port.out.ProductEvent;
import com.ecomm.catalog.application.port.out.ProductEvent.Change;
import com.ecomm.catalog.application.port.out.ProductRepository;
import com.ecomm.catalog.application.port.out.Transactions;
import com.ecomm.catalog.domain.Category;
import com.ecomm.catalog.domain.CategorySummary;
import com.ecomm.catalog.domain.FieldViolation;
import com.ecomm.catalog.domain.InvalidProductException;
import com.ecomm.catalog.domain.PriceCurrencies;
import com.ecomm.catalog.domain.Product;
import com.ecomm.catalog.domain.ProductAlreadyExistsException;
import com.ecomm.catalog.domain.ProductNotFoundException;
import com.ecomm.catalog.domain.VariantIdTakenException;
import com.ecomm.catalog.domain.VariantIdsDroppedException;
import com.ecomm.commons.events.IntegrationEvent;
import com.ecomm.commons.events.IntegrationEventPublisher;
import java.util.Currency;
import java.util.List;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Each change to a Product publishes its {@link ProductEvent} in the change's transaction; a write
 * that is refused publishes nothing.
 */
public class CatalogService
    implements BrowseCatalogUseCase,
        ManageProductsUseCase,
        SeedCatalogUseCase,
        PublishBackfillUseCase {

  private final ProductRepository products;
  private final CategoryRepository categories;
  private final ManageCategoriesUseCase manageCategories;
  private final Transactions transactions;
  private final IntegrationEventPublisher events;

  public CatalogService(
      ProductRepository products,
      CategoryRepository categories,
      ManageCategoriesUseCase manageCategories,
      Transactions transactions,
      IntegrationEventPublisher events) {
    this.products = products;
    this.categories = categories;
    this.manageCategories = manageCategories;
    this.transactions = transactions;
    this.events = events;
  }

  @Override
  public Optional<Product> product(String sku) {
    return products.find(sku);
  }

  @Override
  public List<Currency> currencies() {
    return PriceCurrencies.all();
  }

  @Override
  public Optional<Product> productWithVariant(String variantId) {
    return products.findByVariantId(variantId);
  }

  @Override
  public List<Product> products(Optional<String> category) {
    return category.map(products::findByCategory).orElseGet(products::findAll);
  }

  @Override
  public List<CategorySummary> categories() {
    var counts = products.countByCategory();
    return categories.findAll().stream()
        .map(c -> new CategorySummary(c, counts.getOrDefault(c.slug(), 0L)))
        .toList();
  }

  @Override
  public Optional<CategorySummary> category(String slug) {
    return categories.find(slug).map(c -> new CategorySummary(c, products.countInCategory(slug)));
  }

  @Override
  public Product create(Product product) {
    var arranged = validate(product);
    requireOwnVariantIds(arranged);
    return transactions.inTransaction(
        () -> {
          if (!products.insert(arranged)) {
            throw new ProductAlreadyExistsException(arranged.sku());
          }
          publish(arranged, Change.CREATED);
          return arranged;
        });
  }

  @Override
  public Product update(Product product) {
    var arranged = validate(product);
    var sku = arranged.sku();
    var current = products.find(sku).orElseThrow(() -> new ProductNotFoundException(sku));
    // A Staff write between the read and the replace could slip a drop through; edits are rare.
    var dropped =
        current.variantIds().stream().filter(id -> arranged.variant(id).isEmpty()).toList();
    if (!dropped.isEmpty()) {
      throw new VariantIdsDroppedException(sku, dropped);
    }
    requireOwnVariantIds(arranged);
    return transactions.inTransaction(
        () -> {
          if (!products.replace(arranged)) {
            throw new ProductNotFoundException(sku);
          }
          publish(arranged, Change.UPDATED);
          return arranged;
        });
  }

  @Override
  public void delete(String sku) {
    transactions.inTransaction(
        () -> {
          var removed = products.remove(sku).orElseThrow(() -> new ProductNotFoundException(sku));
          publish(removed, Change.REMOVED);
          return removed;
        });
  }

  @Override
  public boolean seedIfEmpty(List<Category> seedCategories, List<Product> seedProducts) {
    if (!products.isEmpty()) {
      return false;
    }
    seedCategories.forEach(manageCategories::create);
    seedProducts.forEach(this::create);
    return true;
  }

  @Override
  public int publishBackfill() {
    var published = 0;
    for (var category : categories.findAll()) {
      var slug = category.slug();
      if (backfill(
          () -> categories.hasVersion(slug),
          () ->
              categories
                  .find(slug)
                  .map(
                      c ->
                          CategoryEvent.of(
                              c, categories.nextVersion(slug), CategoryEvent.Change.BACKFILLED)))) {
        published++;
      }
    }
    for (var product : products.findAll()) {
      var sku = product.sku();
      if (backfill(
          () -> products.hasVersion(sku),
          () ->
              products
                  .find(sku)
                  .map(p -> ProductEvent.of(p, products.nextVersion(sku), Change.BACKFILLED)))) {
        published++;
      }
    }
    return published;
  }

  /**
   * Publishes the event {@code event} makes of an aggregate that has no version yet, in one
   * transaction; returns whether it did. The version is checked again in the transaction, since a
   * Staff change may have published the aggregate in the meantime.
   */
  private boolean backfill(
      BooleanSupplier hasVersion, Supplier<Optional<? extends IntegrationEvent>> event) {
    return !hasVersion.getAsBoolean()
        && transactions.inTransaction(
            () -> {
              if (hasVersion.getAsBoolean()) {
                return false;
              }
              var published = event.get();
              published.ifPresent(events::publish);
              return published.isPresent();
            });
  }

  private void publish(Product product, Change change) {
    events.publish(ProductEvent.of(product, products.nextVersion(product.sku()), change));
  }

  /**
   * Checks {@code product} against its Category's definitions as they are now, and returns it with
   * its axis values in the Category's order.
   */
  private Product validate(Product product) {
    var category =
        categories
            .find(product.category())
            .orElseThrow(
                () ->
                    new InvalidProductException(
                        List.of(
                            new FieldViolation(
                                "category",
                                "names no Category; create " + product.category() + " first"))));
    var violations = category.violations(product);
    if (!violations.isEmpty()) {
      throw new InvalidProductException(violations);
    }
    return category.arrange(product);
  }

  /**
   * Refuses Variant IDs another Product already has. Two Products written at once with the same new
   * Variant ID can both pass; Staff edit too rarely for that to be worth a lock.
   */
  private void requireOwnVariantIds(Product product) {
    var taken = products.variantIdsOfOtherProducts(product.sku(), product.variantIds());
    if (!taken.isEmpty()) {
      throw new VariantIdTakenException(taken);
    }
  }
}
