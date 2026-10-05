package com.ecomm.catalog.adapter.out.couchbase;

import com.couchbase.client.core.error.DocumentExistsException;
import com.couchbase.client.core.error.DocumentNotFoundException;
import com.couchbase.client.java.Collection;
import com.couchbase.client.java.json.JsonObject;
import com.couchbase.client.java.transactions.TransactionAttemptContext;

/**
 * The version of each Product or Category, which its events carry. It lives in a document of its
 * own, keyed {@code version::<aggregate>::<id>}, which outlives the aggregate: a SKU or slug used
 * again carries on from its last version, so a consumer never mistakes the new one for stale.
 * Raising it inside a transaction also makes two transactions that change one aggregate conflict,
 * so one of them runs again.
 */
final class AggregateVersions {

  private static final String TYPE = "version";

  private final Collection collection;
  private final CouchbaseTransactions transactions;
  private final String aggregate;

  AggregateVersions(Collection collection, CouchbaseTransactions transactions, String aggregate) {
    this.collection = collection;
    this.transactions = transactions;
    this.aggregate = aggregate;
  }

  long next(String id) {
    var context = transactions.required("Raise a version");
    var key = key(id);
    try {
      return raise(context, key, id);
    } catch (DocumentNotFoundException e) {
      try {
        context.insert(collection, key, document(id, 1));
        return 1;
      } catch (DocumentExistsException raced) {
        // Another transaction gave it its first version since; carry on from that one.
        return raise(context, key, id);
      }
    }
  }

  private long raise(TransactionAttemptContext context, String key, String id) {
    var current = context.get(collection, key);
    var next = current.contentAsObject().getLong("version") + 1;
    context.replace(current, document(id, next));
    return next;
  }

  /** Read in the running transaction, if any, so that a conflicting one runs again. */
  boolean exists(String id) {
    var key = key(id);
    return transactions
        .active()
        .map(
            context -> {
              try {
                context.get(collection, key);
                return true;
              } catch (DocumentNotFoundException e) {
                return false;
              }
            })
        .orElseGet(() -> collection.exists(key).exists());
  }

  private String key(String id) {
    return TYPE + "::" + aggregate + "::" + id;
  }

  private JsonObject document(String id, long version) {
    return JsonObject.create()
        .put("type", TYPE)
        .put("aggregate", aggregate)
        .put("id", id)
        .put("version", version);
  }
}
