package com.ecomm.catalog.application;

import com.ecomm.catalog.application.port.in.ManageCategoriesUseCase;
import com.ecomm.catalog.application.port.out.CategoryEvent;
import com.ecomm.catalog.application.port.out.CategoryEvent.Change;
import com.ecomm.catalog.application.port.out.CategoryRepository;
import com.ecomm.catalog.application.port.out.ProductRepository;
import com.ecomm.catalog.application.port.out.Transactions;
import com.ecomm.catalog.domain.Category;
import com.ecomm.catalog.domain.CategoryAlreadyExistsException;
import com.ecomm.catalog.domain.CategoryInUseException;
import com.ecomm.catalog.domain.CategoryNotFoundException;
import com.ecomm.commons.events.IntegrationEventPublisher;

/** Each change to a Category publishes its {@link CategoryEvent} in the change's transaction. */
public class CategoryService implements ManageCategoriesUseCase {

  private final CategoryRepository categories;
  private final ProductRepository products;
  private final Transactions transactions;
  private final IntegrationEventPublisher events;

  public CategoryService(
      CategoryRepository categories,
      ProductRepository products,
      Transactions transactions,
      IntegrationEventPublisher events) {
    this.categories = categories;
    this.products = products;
    this.transactions = transactions;
    this.events = events;
  }

  @Override
  public Category create(Category category) {
    return transactions.inTransaction(
        () -> {
          if (!categories.insert(category)) {
            throw new CategoryAlreadyExistsException(category.slug());
          }
          publish(category, Change.CREATED);
          return category;
        });
  }

  @Override
  public Category update(Category category) {
    return transactions.inTransaction(
        () -> {
          if (!categories.replace(category)) {
            throw new CategoryNotFoundException(category.slug());
          }
          publish(category, Change.UPDATED);
          return category;
        });
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
    transactions.inTransaction(
        () -> {
          var removed =
              categories.remove(slug).orElseThrow(() -> new CategoryNotFoundException(slug));
          publish(removed, Change.REMOVED);
          return removed;
        });
  }

  private void publish(Category category, Change change) {
    events.publish(CategoryEvent.of(category, categories.nextVersion(category.slug()), change));
  }
}
