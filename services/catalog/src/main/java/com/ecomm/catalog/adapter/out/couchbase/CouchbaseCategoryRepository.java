package com.ecomm.catalog.adapter.out.couchbase;

import static com.couchbase.client.java.query.QueryOptions.queryOptions;

import com.couchbase.client.core.error.DocumentExistsException;
import com.couchbase.client.core.error.DocumentNotFoundException;
import com.couchbase.client.java.Cluster;
import com.couchbase.client.java.Collection;
import com.couchbase.client.java.Scope;
import com.couchbase.client.java.json.JsonArray;
import com.couchbase.client.java.json.JsonObject;
import com.couchbase.client.java.query.QueryScanConsistency;
import com.ecomm.catalog.application.port.out.CategoryRepository;
import com.ecomm.catalog.domain.AttributeDefinition;
import com.ecomm.catalog.domain.AttributeType;
import com.ecomm.catalog.domain.Category;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Stores each Category as a JSON document marked {@code "type": "category"} in the bucket's default
 * collection, beside the Products. Its key is {@code category::} and the slug, so it never clashes
 * with a SKU. Listings go through a query index on {@code slug}, created on startup if missing.
 */
@Component
class CouchbaseCategoryRepository implements CategoryRepository {

  private static final String TYPE = "category";

  private final Scope scope;
  private final Collection collection;
  private final CouchbaseTransactions transactions;
  private final AggregateVersions versions;

  CouchbaseCategoryRepository(
      Cluster cluster,
      @Value("${ecomm.catalog.bucket}") String bucketName,
      CouchbaseTransactions transactions) {
    var bucket = cluster.bucket(bucketName);
    bucket.waitUntilReady(Duration.ofSeconds(60));
    this.scope = bucket.defaultScope();
    this.collection = bucket.defaultCollection();
    this.transactions = transactions;
    this.versions = new AggregateVersions(collection, transactions, TYPE);
    scope.query(
        "CREATE INDEX idx_category_slug IF NOT EXISTS ON `_default`(slug)"
            + " WHERE type = 'category'");
  }

  @Override
  public Optional<Category> find(String slug) {
    try {
      return Optional.of(fromDocument(collection.get(key(slug)).contentAsObject()));
    } catch (DocumentNotFoundException e) {
      return Optional.empty();
    }
  }

  @Override
  public List<Category> findAll() {
    return scope
        .query(
            "SELECT RAW c FROM `_default` c WHERE c.type = 'category' AND c.slug IS NOT MISSING"
                + " ORDER BY c.slug",
            queryOptions().scanConsistency(QueryScanConsistency.REQUEST_PLUS))
        .rowsAsObject()
        .stream()
        .map(CouchbaseCategoryRepository::fromDocument)
        .toList();
  }

  @Override
  public boolean insert(Category category) {
    try {
      transactions
          .required("Store a Category")
          .insert(collection, key(category.slug()), toDocument(category));
      return true;
    } catch (DocumentExistsException e) {
      return false;
    }
  }

  @Override
  public boolean replace(Category category) {
    var context = transactions.required("Store a Category");
    try {
      context.replace(context.get(collection, key(category.slug())), toDocument(category));
      return true;
    } catch (DocumentNotFoundException e) {
      return false;
    }
  }

  @Override
  public Optional<Category> remove(String slug) {
    var context = transactions.required("Remove a Category");
    try {
      var current = context.get(collection, key(slug));
      context.remove(current);
      return Optional.of(fromDocument(current.contentAsObject()));
    } catch (DocumentNotFoundException e) {
      return Optional.empty();
    }
  }

  @Override
  public long nextVersion(String slug) {
    return versions.next(slug);
  }

  @Override
  public boolean hasVersion(String slug) {
    return versions.exists(slug);
  }

  private static String key(String slug) {
    return TYPE + "::" + slug;
  }

  private static JsonObject toDocument(Category category) {
    var attributes = JsonArray.create();
    category
        .attributes()
        .forEach(
            d ->
                attributes.add(
                    JsonObject.create()
                        .put("name", d.name())
                        .put("type", d.type().name())
                        .put("values", JsonArray.from(List.copyOf(d.values())))
                        .put("required", d.required())
                        .put("variantAxis", d.variantAxis())));
    return JsonObject.create()
        .put("type", TYPE)
        .put("slug", category.slug())
        .put("name", category.name())
        .put("attributes", attributes);
  }

  private static Category fromDocument(JsonObject document) {
    var definitions = document.getArray("attributes");
    var attributes =
        IntStream.range(0, definitions.size())
            .mapToObj(definitions::getObject)
            .map(
                d ->
                    new AttributeDefinition(
                        d.getString("name"),
                        AttributeType.valueOf(d.getString("type")),
                        d.getArray("values").toList().stream().map(String::valueOf).toList(),
                        d.getBoolean("required"),
                        d.getBoolean("variantAxis")))
            .toList();
    return new Category(document.getString("slug"), document.getString("name"), attributes);
  }
}
