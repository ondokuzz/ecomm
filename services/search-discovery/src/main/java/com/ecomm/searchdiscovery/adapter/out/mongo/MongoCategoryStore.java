package com.ecomm.searchdiscovery.adapter.out.mongo;

import com.ecomm.searchdiscovery.application.port.out.CategoryStore;
import com.ecomm.searchdiscovery.domain.AttributeDefinition;
import com.ecomm.searchdiscovery.domain.AttributeType;
import com.ecomm.searchdiscovery.domain.SearchCategory;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.ReplaceOptions;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

/** Categories in the {@code categories} collection, keyed by slug. */
@Component
class MongoCategoryStore implements CategoryStore {

  static final String COLLECTION = "categories";

  private final MongoCollection<Document> categories;

  MongoCategoryStore(MongoTemplate mongo) {
    this.categories = mongo.getCollection(COLLECTION);
  }

  @Override
  public Optional<SearchCategory> find(String slug) {
    return Optional.ofNullable(categories.find(new Document("_id", slug)).first())
        .map(MongoCategoryStore::category);
  }

  @Override
  public void save(SearchCategory category) {
    categories.replaceOne(
        new Document("_id", category.slug()),
        new Document("_id", category.slug())
            .append("version", category.version())
            .append("name", category.name())
            .append(
                "attributes",
                category.attributes().stream()
                    .map(
                        a ->
                            new Document("name", a.name())
                                .append("type", a.type().name())
                                .append("values", a.values()))
                    .toList())
            .append("removed", category.removed()),
        new ReplaceOptions().upsert(true));
  }

  @Override
  public List<SearchCategory> listed() {
    return categories
        .find(new Document("removed", false))
        .map(MongoCategoryStore::category)
        .into(new ArrayList<>());
  }

  private static SearchCategory category(Document document) {
    return new SearchCategory(
        document.getString("_id"),
        document.getLong("version"),
        document.getString("name"),
        document.getList("attributes", Document.class).stream()
            .map(
                a ->
                    new AttributeDefinition(
                        a.getString("name"),
                        AttributeType.valueOf(a.getString("type")),
                        a.getList("values", String.class)))
            .toList(),
        document.getBoolean("removed"));
  }
}
