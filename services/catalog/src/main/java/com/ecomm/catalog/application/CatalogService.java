package com.ecomm.catalog.application;

import com.ecomm.catalog.application.port.in.BrowseCatalogUseCase;
import com.ecomm.catalog.application.port.in.ManageProductsUseCase;
import com.ecomm.catalog.application.port.in.SeedCatalogUseCase;
import com.ecomm.catalog.application.port.out.ProductRepository;
import com.ecomm.catalog.domain.CategorySummary;
import com.ecomm.catalog.domain.Product;
import com.ecomm.catalog.domain.ProductAlreadyExistsException;
import com.ecomm.catalog.domain.ProductNotFoundException;
import java.util.List;
import java.util.Optional;

public class CatalogService
    implements BrowseCatalogUseCase, ManageProductsUseCase, SeedCatalogUseCase {

  private final ProductRepository products;

  public CatalogService(ProductRepository products) {
    this.products = products;
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
    return products.categories();
  }

  @Override
  public Product create(Product product) {
    if (!products.insert(product)) {
      throw new ProductAlreadyExistsException(product.sku());
    }
    return product;
  }

  @Override
  public Product update(Product product) {
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
  public boolean seedIfEmpty(List<Product> seed) {
    if (!products.isEmpty()) {
      return false;
    }
    seed.forEach(products::insert);
    return true;
  }
}
