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
import com.ecomm.catalog.domain.Variant;
import com.ecomm.commons.money.Money;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Stores each Product, its Variants inside it, as a JSON document keyed by its SKU in the bucket's
 * default collection. Reads by SKU are key-value lookups; listings go through a query index on
 * {@code category}, and lookups by Variant ID through an array index on the Variants' IDs, both
 * created on startup if missing. Queries wait for the indexes to catch up, so a change is visible
 * to the next query.
 */
@Component
class CouchbaseProductRepository implements ProductRepository {

  private static final String TYPE = "product";

  private final Scope scope;
  private final Collection collection;
  private final CouchbaseTransactions transactions;
  private final AggregateVersions versions;

  CouchbaseProductRepository(
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
        "CREATE INDEX idx_product_category IF NOT EXISTS ON `_default`(category, name)"
            + " WHERE type = 'product'");
    scope.query(
        "CREATE INDEX idx_product_variant_id IF NOT EXISTS"
            + " ON `_default`(DISTINCT ARRAY v.id FOR v IN variants END) WHERE type = 'product'");
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
  public Optional<Product> findByVariantId(String variantId) {
    return query(
            "SELECT RAW p FROM `_default` p WHERE p.type = 'product'"
                + " AND ANY v IN p.variants SATISFIES v.id = $id END",
            consistent().parameters(JsonObject.create().put("id", variantId)))
        .stream()
        .findFirst();
  }

  @Override
  public List<String> variantIdsOfOtherProducts(String sku, List<String> variantIds) {
    var taken =
        scope
            .query(
                "SELECT RAW ARRAY v.id FOR v IN p.variants END FROM `_default` p"
                    + " WHERE p.type = 'product' AND ANY v IN p.variants SATISFIES v.id IN $ids END"
                    + " AND p.sku != $sku",
                consistent()
                    .parameters(
                        JsonObject.create().put("ids", JsonArray.from(variantIds)).put("sku", sku)))
            .rowsAs(String[].class)
            .stream()
            .flatMap(Arrays::stream)
            .collect(Collectors.toSet());
    return variantIds.stream().filter(taken::contains).toList();
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
      transactions
          .required("Store a Product")
          .insert(collection, product.sku(), toDocument(product));
      return true;
    } catch (DocumentExistsException e) {
      return false;
    }
  }

  @Override
  public boolean replace(Product product) {
    var context = transactions.required("Store a Product");
    try {
      context.replace(context.get(collection, product.sku()), toDocument(product));
      return true;
    } catch (DocumentNotFoundException e) {
      return false;
    }
  }

  @Override
  public Optional<Product> remove(String sku) {
    var context = transactions.required("Remove a Product");
    try {
      var current = context.get(collection, sku);
      context.remove(current);
      return Optional.of(fromDocument(current.contentAsObject()));
    } catch (DocumentNotFoundException e) {
      return Optional.empty();
    }
  }

  @Override
  public long nextVersion(String sku) {
    return versions.next(sku);
  }

  @Override
  public boolean hasVersion(String sku) {
    return versions.exists(sku);
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
        .put("description", product.description())
        .put("category", product.category())
        .put("attributes", JsonObject.from(Map.copyOf(product.attributes())))
        .put("images", JsonArray.from(List.copyOf(product.images())))
        .put(
            "variants",
            JsonArray.from(
                product.variants().stream().map(CouchbaseProductRepository::toDocument).toList()));
  }

  /** Axis values are stored as a list of name-value pairs, since a JSON object keeps no order. */
  private static JsonObject toDocument(Variant variant) {
    var axisValues = JsonArray.create();
    variant
        .axisValues()
        .forEach(
            (name, value) ->
                axisValues.add(JsonObject.create().put("name", name).put("value", value)));
    return JsonObject.create()
        .put("id", variant.id())
        .put("axisValues", axisValues)
        .put(
            "price",
            JsonObject.create()
                .put("amountMinor", variant.price().amountMinor())
                .put("currency", variant.price().currency().getCurrencyCode()))
        .put("images", JsonArray.from(List.copyOf(variant.images())));
  }

  private static Product fromDocument(JsonObject document) {
    var attributes =
        document.getObject("attributes").toMap().entrySet().stream()
            .collect(Collectors.toMap(Map.Entry::getKey, e -> String.valueOf(e.getValue())));
    var variants = new ArrayList<Variant>();
    for (var variant : document.getArray("variants")) {
      variants.add(variantFrom((JsonObject) variant));
    }
    return new Product(
        document.getString("sku"),
        document.getString("name"),
        document.getString("description"),
        document.getString("category"),
        attributes,
        strings(document.getArray("images")),
        variants);
  }

  private static Variant variantFrom(JsonObject document) {
    var axisValues = new LinkedHashMap<String, String>();
    for (var pair : document.getArray("axisValues")) {
      var axisValue = (JsonObject) pair;
      axisValues.put(axisValue.getString("name"), axisValue.getString("value"));
    }
    var price = document.getObject("price");
    return new Variant(
        document.getString("id"),
        axisValues,
        Money.of(price.getLong("amountMinor"), price.getString("currency")),
        strings(document.getArray("images")));
  }

  private static List<String> strings(JsonArray array) {
    return array.toList().stream().map(String::valueOf).toList();
  }
}
