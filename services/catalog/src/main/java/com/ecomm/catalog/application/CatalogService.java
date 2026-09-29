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
    validate(product);
    if (!products.insert(product)) {
      throw new ProductAlreadyExistsException(product.sku());
    }
    return product;
  }

  @Override
  public Product update(Product product) {
    validate(product);
    if (!products.replace(product)) {
      throw new ProductNotFoundException(product.sku());
    }
    return product;
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

  /** Checks {@code product} against its Category's definitions as they are now. */
  private void validate(Product product) {
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
    var violations = category.violations(product.attributes());
    if (!violations.isEmpty()) {
      throw new InvalidProductException(violations);
    }
  }
}
