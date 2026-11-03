package com.ecomm.searchdiscovery.adapter.out.mongo;

import com.ecomm.commons.money.Money;
import com.ecomm.searchdiscovery.application.port.out.ProductStore;
import com.ecomm.searchdiscovery.domain.Candidate;
import com.ecomm.searchdiscovery.domain.ListedProduct;
import com.ecomm.searchdiscovery.domain.SearchableProduct;
import com.ecomm.searchdiscovery.domain.SearchableVariant;
import com.ecomm.searchdiscovery.domain.VariantStock;
import com.ecomm.searchdiscovery.domain.WarrantyTerms;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.ReplaceOptions;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

/**
 * Products in the {@code products} collection, one document per SKU. On startup it creates {@code
 * text}, the text index searches run on: over the name, weighted highest, the attribute and axis
 * values, and the description.
 */
@Component
class MongoProductStore implements ProductStore {

  static final String COLLECTION = "products";

  private final MongoCollection<Document> products;

  MongoProductStore(MongoTemplate mongo) {
    this.products = mongo.getCollection(COLLECTION);
    products.createIndex(
        Indexes.compoundIndex(
            Indexes.text("name"), Indexes.text("attributeValues"), Indexes.text("description")),
        new IndexOptions()
            .name("text")
            .weights(
                new Document("name", 10).append("attributeValues", 5).append("description", 1)));
  }

  @Override
  public Optional<SearchableProduct> find(String sku) {
    return Optional.ofNullable(products.find(new Document("_id", sku)).first())
        .map(MongoProductStore::product);
  }

  /**
   * One aggregation, as for {@link #candidates}: the Product, its Category joined by slug, and its
   * Variants' Stock.
   */
  @Override
  public Optional<ListedProduct> listed(String sku) {
    var pipeline =
        List.of(
            new Document("$match", new Document("_id", sku).append("removed", false)),
            new Document(
                "$lookup",
                new Document("from", MongoCategoryStore.COLLECTION)
                    .append("localField", "category")
                    .append("foreignField", "_id")
                    .append("as", "categoryOf")),
            stockLookup());
    return Optional.ofNullable(products.aggregate(pipeline).first())
        .map(
            document ->
                new ListedProduct(
                    product(document),
                    document.getList("categoryOf", Document.class).stream()
                        .filter(c -> !c.getBoolean("removed"))
                        .map(c -> c.getString("name"))
                        .findFirst()
                        .orElse(null),
                    stockOf(document)
                        .filter(VariantStock::inStock)
                        .map(VariantStock::variantId)
                        .collect(Collectors.toSet())));
  }

  @Override
  public void save(SearchableProduct product) {
    products.replaceOne(
        new Document("_id", product.sku()), document(product), new ReplaceOptions().upsert(true));
  }

  /** One aggregation: the text match, then each Product's Variants' Stock, joined by Variant ID. */
  @Override
  public List<Candidate> candidates(String text) {
    var match = new Document("removed", false);
    if (text != null) {
      match.append("$text", new Document("$search", text));
    }
    var pipeline = new ArrayList<Document>();
    pipeline.add(new Document("$match", match));
    pipeline.add(stockLookup());
    if (text != null) {
      pipeline.add(
          new Document(
              "$addFields", new Document("relevance", new Document("$meta", "textScore"))));
    }
    var candidates = new ArrayList<Candidate>();
    for (var document : products.aggregate(pipeline)) {
      candidates.add(
          new Candidate(
              product(document),
              stockOf(document).anyMatch(VariantStock::inStock),
              document.get("relevance", 0.0)));
    }
    return candidates;
  }

  /** Joins each of a Product's Variants' Stock from {@code stock}, as {@code stock}. */
  private static Document stockLookup() {
    return new Document(
        "$lookup",
        new Document("from", MongoStockStore.COLLECTION)
            .append("localField", "variants.variantId")
            .append("foreignField", "_id")
            .append("as", "stock"));
  }

  private static Stream<VariantStock> stockOf(Document document) {
    return document.getList("stock", Document.class).stream().map(MongoStockStore::stock);
  }

  private static Document document(SearchableProduct product) {
    return new Document("_id", product.sku())
        .append("version", product.version())
        .append("name", product.name())
        .append("description", product.description())
        .append("category", product.category())
        .append("attributes", Pairs.of(product.attributes()))
        .append("attributeValues", List.copyOf(product.attributeValues()))
        .append("images", product.images())
        .append(
            "variants",
            product.variants().stream()
                .map(
                    v ->
                        new Document("variantId", v.variantId())
                            .append("axisValues", Pairs.of(v.axisValues()))
                            .append(
                                "price",
                                new Document("amountMinor", v.price().amountMinor())
                                    .append("currency", v.price().currency().getCurrencyCode()))
                            .append("images", v.images()))
                .toList())
        .append(
            "warranty",
            product.warranty() == null
                ? null
                : new Document("months", product.warranty().months())
                    .append("manufacturer", product.warranty().manufacturer()))
        .append("removed", product.removed())
        .append("listedAt", Date.from(product.listedAt()));
  }

  private static SearchableProduct product(Document document) {
    return new SearchableProduct(
        document.getString("_id"),
        document.getLong("version"),
        document.getString("name"),
        document.getString("description"),
        document.getString("category"),
        Pairs.from(document.getList("attributes", Document.class)),
        document.getList("images", String.class),
        document.getList("variants", Document.class).stream()
            .map(
                v -> {
                  var price = v.get("price", Document.class);
                  return new SearchableVariant(
                      v.getString("variantId"),
                      Pairs.from(v.getList("axisValues", Document.class)),
                      Money.of(price.getLong("amountMinor"), price.getString("currency")),
                      v.getList("images", String.class));
                })
            .toList(),
        warranty(document.get("warranty", Document.class)),
        document.getBoolean("removed"),
        document.getDate("listedAt").toInstant());
  }

  /** Null when it has none, as for a Product stored before Warranty terms were projected. */
  private static WarrantyTerms warranty(Document warranty) {
    return warranty == null
        ? null
        : new WarrantyTerms(warranty.getInteger("months"), warranty.getString("manufacturer"));
  }
}
