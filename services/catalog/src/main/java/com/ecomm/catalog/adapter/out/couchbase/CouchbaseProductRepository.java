package com.ecomm.catalog.adapter.out.couchbase;

import static com.couchbase.client.java.query.QueryOptions.queryOptions;

import com.couchbase.client.core.error.DocumentExistsException;
import com.couchbase.client.core.error.DocumentNotFoundException;
import com.couchbase.client.java.Cluster;
import com.couchbase.client.java.Collection;
import com.couchbase.client.java.Scope;
import com.couchbase.client.java.json.JsonArray;
import com.couchbase.client.java.json.JsonObject;
import com.couchbase.client.java.query.QueryOptions;
import com.couchbase.client.java.query.QueryScanConsistency;
import com.ecomm.catalog.application.port.out.ProductRepository;
import com.ecomm.catalog.domain.Product;
import com.ecomm.commons.money.Money;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Stores each Product as a JSON document keyed by its SKU in the bucket's default collection. Reads
 * by SKU are key-value lookups; listings go through a query index on {@code category}, created on
 * startup if missing. Queries wait for the index to catch up, so a change is visible to the next
 * listing.
 */
@Component
class CouchbaseProductRepository implements ProductRepository {

  private static final String TYPE = "product";

  private final Scope scope;
  private final Collection collection;

  CouchbaseProductRepository(Cluster cluster, @Value("${ecomm.catalog.bucket}") String bucketName) {
    var bucket = cluster.bucket(bucketName);
    bucket.waitUntilReady(Duration.ofSeconds(60));
    this.scope = bucket.defaultScope();
    this.collection = bucket.defaultCollection();
    scope.query(
        "CREATE INDEX idx_product_category IF NOT EXISTS ON `_default`(category, name)"
            + " WHERE type = 'product'");
  }

  @Override
  public Optional<Product> find(String sku) {
    try {
      return Optional.of(fromDocument(collection.get(sku).contentAsObject()));
    } catch (DocumentNotFoundException e) {
      return Optional.empty();
    }
  }

  @Override
  public List<Product> findAll() {
    return query(
        "SELECT RAW p FROM `_default` p WHERE p.type = 'product' AND p.category IS NOT MISSING"
            + " ORDER BY p.name",
        consistent());
  }

  @Override
  public List<Product> findByCategory(String category) {
    return query(
        "SELECT RAW p FROM `_default` p WHERE p.type = 'product' AND p.category = $category"
            + " ORDER BY p.name",
        consistent().parameters(JsonObject.create().put("category", category)));
  }

  @Override
  public Map<String, Long> countByCategory() {
    return scope
        .query(
            "SELECT p.category, COUNT(*) AS productCount FROM `_default` p"
                + " WHERE p.type = 'product' AND p.category IS NOT MISSING"
                + " GROUP BY p.category",
            consistent())
        .rowsAsObject()
        .stream()
        .collect(
            Collectors.toMap(row -> row.getString("category"), row -> row.getLong("productCount")));
  }

  @Override
  public long countInCategory(String category) {
    return scope
        .query(
            "SELECT RAW COUNT(*) FROM `_default` p WHERE p.type = 'product' AND p.category ="
                + " $category",
            consistent().parameters(JsonObject.create().put("category", category)))
        .rowsAs(Long.class)
        .getFirst();
  }

  @Override
  public boolean isEmpty() {
    // COUNT(*) with no WHERE clause reads the collection's item count; it needs no index.
    return scope
            .query("SELECT RAW COUNT(*) FROM `_default`", consistent())
            .rowsAs(Long.class)
            .getFirst()
        == 0;
  }

  @Override
  public boolean insert(Product product) {
    try {
      collection.insert(product.sku(), toDocument(product));
      return true;
    } catch (DocumentExistsException e) {
      return false;
    }
  }

  @Override
  public boolean replace(Product product) {
    try {
      collection.replace(product.sku(), toDocument(product));
      return true;
    } catch (DocumentNotFoundException e) {
      return false;
    }
  }

  @Override
  public boolean remove(String sku) {
    try {
      collection.remove(sku);
      return true;
    } catch (DocumentNotFoundException e) {
      return false;
    }
  }

  private List<Product> query(String statement, QueryOptions options) {
    return scope.query(statement, options).rowsAsObject().stream()
        .map(CouchbaseProductRepository::fromDocument)
        .toList();
  }

  private static QueryOptions consistent() {
    return queryOptions().scanConsistency(QueryScanConsistency.REQUEST_PLUS);
  }

  private static JsonObject toDocument(Product product) {
    return JsonObject.create()
        .put("type", TYPE)
        .put("sku", product.sku())
        .put("name", product.name())
        .put("category", product.category())
        .put("attributes", JsonObject.from(Map.copyOf(product.attributes())))
        .put(
            "price",
            JsonObject.create()
                .put("amountMinor", product.price().amountMinor())
                .put("currency", product.price().currency().getCurrencyCode()))
        .put("images", JsonArray.from(List.copyOf(product.images())));
  }

  private static Product fromDocument(JsonObject document) {
    var price = document.getObject("price");
    var attributes =
        document.getObject("attributes").toMap().entrySet().stream()
            .collect(Collectors.toMap(Map.Entry::getKey, e -> String.valueOf(e.getValue())));
    var images = document.getArray("images").toList().stream().map(String::valueOf).toList();
    return new Product(
        document.getString("sku"),
        document.getString("name"),
        document.getString("category"),
        attributes,
        Money.of(price.getLong("amountMinor"), price.getString("currency")),
        images);
  }
}
