package com.ecomm.searchdiscovery.adapter.out.mongo;

import com.ecomm.searchdiscovery.application.port.out.StockStore;
import com.ecomm.searchdiscovery.domain.VariantStock;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.ReplaceOptions;
import java.util.Optional;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

/** Each Variant's Stock in the {@code stock} collection, keyed by its Variant ID. */
@Component
class MongoStockStore implements StockStore {

  static final String COLLECTION = "stock";

  private final MongoCollection<Document> stock;

  MongoStockStore(MongoTemplate mongo) {
    this.stock = mongo.getCollection(COLLECTION);
  }

  @Override
  public Optional<VariantStock> find(String variantId) {
    return Optional.ofNullable(stock.find(new Document("_id", variantId)).first())
        .map(MongoStockStore::stock);
  }

  @Override
  public void save(VariantStock variant) {
    stock.replaceOne(
        new Document("_id", variant.variantId()),
        new Document("_id", variant.variantId())
            .append("version", variant.version())
            .append("available", variant.available())
            .append("stocked", variant.stocked()),
        new ReplaceOptions().upsert(true));
  }

  static VariantStock stock(Document document) {
    return new VariantStock(
        document.getString("_id"),
        document.getLong("version"),
        document.getLong("available"),
        document.getBoolean("stocked"));
  }
}
