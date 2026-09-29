package com.ecomm.catalog.application;

import com.ecomm.catalog.application.port.in.ManageCategoriesUseCase;
import com.ecomm.catalog.application.port.out.CategoryRepository;
import com.ecomm.catalog.application.port.out.ProductRepository;
import com.ecomm.catalog.domain.Category;
import com.ecomm.catalog.domain.CategoryAlreadyExistsException;
import com.ecomm.catalog.domain.CategoryInUseException;
import com.ecomm.catalog.domain.CategoryNotFoundException;

public class CategoryService implements ManageCategoriesUseCase {

  private final CategoryRepository categories;
  private final ProductRepository products;

  public CategoryService(CategoryRepository categories, ProductRepository products) {
    this.categories = categories;
    this.products = products;
  }

  @Override
  public Category create(Category category) {
    if (!categories.insert(category)) {
      throw new CategoryAlreadyExistsException(category.slug());
    }
    return category;
  }

  @Override
  public Category update(Category category) {
    if (!categories.replace(category)) {
      throw new CategoryNotFoundException(category.slug());
    }
    return category;
  }

  @Override
  public void delete(String slug) {
    if (categories.find(slug).isEmpty()) {
      throw new CategoryNotFoundException(slug);
    }
    // A Product created between this check and the removal is left without a Category; its next
    // write is refused until Staff recreate the Category.
    if (products.countInCategory(slug) > 0) {
      throw new CategoryInUseException(slug);
    }
    if (!categories.remove(slug)) {
      throw new CategoryNotFoundException(slug);
    }
  }
}
