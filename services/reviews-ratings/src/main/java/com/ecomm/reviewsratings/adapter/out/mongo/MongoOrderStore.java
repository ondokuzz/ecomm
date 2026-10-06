package com.ecomm.reviewsratings.adapter.out.mongo;

import com.ecomm.reviewsratings.application.port.out.OrderStore;
import com.ecomm.reviewsratings.domain.Order;
import com.ecomm.reviewsratings.domain.OrderStatus;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.ReplaceOptions;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

/** Orders in the {@code orders} collection, keyed by Order ID and indexed by Customer. */
@Component
class MongoOrderStore implements OrderStore {

  private final MongoCollection<Document> orders;

  MongoOrderStore(MongoTemplate mongo) {
    this.orders = mongo.getCollection("orders");
    orders.createIndex(Indexes.ascending("customerId"));
  }

  @Override
  public Optional<Order> find(String orderId) {
    return Optional.ofNullable(orders.find(new Document("_id", orderId)).first())
        .map(MongoOrderStore::order);
  }

  @Override
  public void save(Order order) {
    orders.replaceOne(
        new Document("_id", order.orderId()),
        new Document("_id", order.orderId())
            .append("version", order.version())
            .append("customerId", order.customerId())
            .append("status", order.status().name())
            .append("placedAt", Date.from(order.placedAt()))
            .append("variantIds", order.variantIds()),
        new ReplaceOptions().upsert(true));
  }

  @Override
  public List<Order> ofCustomer(String customerId) {
    var found = new ArrayList<Order>();
    for (var document : orders.find(new Document("customerId", customerId))) {
      found.add(order(document));
    }
    return found;
  }

  private static Order order(Document document) {
    return new Order(
        document.getString("_id"),
        document.getLong("version"),
        document.getString("customerId"),
        OrderStatus.valueOf(document.getString("status")),
        document.getDate("placedAt").toInstant(),
        document.getList("variantIds", String.class));
  }
}
