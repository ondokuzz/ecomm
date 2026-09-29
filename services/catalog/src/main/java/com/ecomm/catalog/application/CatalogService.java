package com.ecomm.catalog.application;

import com.ecomm.catalog.application.port.in.BrowseCatalogUseCase;
import com.ecomm.catalog.application.port.in.ManageProductsUseCase;
import com.ecomm.catalog.application.port.in.SeedCatalogUseCase;
import com.ecomm.catalog.application.port.out.CategoryRepository;
import com.ecomm.catalog.application.port.out.ProductRepository;
import com.ecomm.catalog.domain.Category;
import com.ecomm.catalog.domain.CategorySummary;
import com.ecomm.catalog.domain.FieldViolation;
import com.ecomm.catalog.domain.InvalidProductException;
import com.ecomm.catalog.domain.Product;
import com.ecomm.catalog.domain.ProductAlreadyExistsException;
import com.ecomm.catalog.domain.ProductNotFoundException;
import com.ecomm.catalog.domain.VariantIdTakenException;
import com.ecomm.catalog.domain.VariantIdsDroppedException;
import java.util.List;
import java.util.Optional;

public class CatalogService
    implements BrowseCatalogUseCase, ManageProductsUseCase, SeedCatalogUseCase {

  private final ProductRepository products;
  private final CategoryRepository categories;

  public CatalogService(ProductRepository products, CategoryRepository categories) {
    this.products = products;
    this.categories = categories;
  }

  @Override
  public Optional<Product> product(String sku) {
    return products.find(sku);
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
    product = validate(product);
    requireOwnVariantIds(product);
    if (!products.insert(product)) {
      throw new ProductAlreadyExistsException(product.sku());
    }
    return product;
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
    if (!products.replace(arranged)) {
      throw new ProductNotFoundException(sku);
    }
    return arranged;
  }

  @Override
  public void delete(String sku) {
    if (!products.remove(sku)) {
      throw new ProductNotFoundException(sku);
    }
  }

  @Override
  public boolean seedIfEmpty(List<Category> seedCategories, List<Product> seedProducts) {
    if (!products.isEmpty()) {
      return false;
    }
    seedCategories.forEach(categories::insert);
    seedProducts.forEach(this::create);
    return true;
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
