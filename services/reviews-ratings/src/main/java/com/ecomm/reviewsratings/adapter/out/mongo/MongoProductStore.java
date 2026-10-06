package com.ecomm.reviewsratings.adapter.out.mongo;

import com.ecomm.reviewsratings.application.port.out.ProductStore;
import com.ecomm.reviewsratings.domain.ProductVariants;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.ReplaceOptions;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

/** Each Product's Variant IDs in the {@code products} collection, keyed by SKU. */
@Component
class MongoProductStore implements ProductStore {

  private final MongoCollection<Document> products;

  MongoProductStore(MongoTemplate mongo) {
    this.products = mongo.getCollection("products");
  }

  @Override
  public Optional<ProductVariants> find(String sku) {
    return Optional.ofNullable(products.find(new Document("_id", sku)).first())
        .map(
            d ->
                new ProductVariants(
                    d.getString("_id"),
                    d.getLong("version"),
                    Set.copyOf(d.getList("variantIds", String.class))));
  }

  @Override
  public void save(ProductVariants product) {
    products.replaceOne(
        new Document("_id", product.sku()),
        new Document("_id", product.sku())
            .append("version", product.version())
            .append("variantIds", List.copyOf(product.variantIds())),
        new ReplaceOptions().upsert(true));
  }
}
