package com.ecomm.catalog.adapter.out.couchbase;

import com.couchbase.client.core.error.CouchbaseException;
import com.couchbase.client.core.msg.kv.DurabilityLevel;
import com.couchbase.client.java.Cluster;
import com.couchbase.client.java.transactions.TransactionAttemptContext;
import com.couchbase.client.java.transactions.config.TransactionsConfig;
import com.couchbase.client.java.transactions.error.TransactionFailedException;
import com.ecomm.catalog.application.port.out.Transactions;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.couchbase.autoconfigure.ClusterEnvironmentBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

/**
 * Runs work in a Couchbase distributed transaction. The SDK runs the work on a thread of its own,
 * so the attempt is bound to that thread, for the repositories and the outbox to write through, and
 * the caller's logging context, Correlation ID included, is carried over to it. Couchbase runs the
 * work again when an attempt conflicts with another transaction, and stages each write until the
 * commit, so other readers see none of them before it.
 */
@Component
class CouchbaseTransactions implements Transactions {

  private final Cluster cluster;
  private final ThreadLocal<TransactionAttemptContext> attempt = new ThreadLocal<>();

  CouchbaseTransactions(Cluster cluster) {
    this.cluster = cluster;
  }

  @Override
  public <T> T inTransaction(Supplier<T> work) {
    if (attempt.get() != null) {
      throw new IllegalStateException("Transactions don't nest");
    }
    var result = new AtomicReference<T>();
    var callerLogContext = MDC.getCopyOfContextMap();
    try {
      cluster
          .transactions()
          .run(
              context -> {
                var ownLogContext = MDC.getCopyOfContextMap();
                setLogContext(callerLogContext);
                attempt.set(context);
                try {
                  result.set(work.get());
                } finally {
                  attempt.remove();
                  setLogContext(ownLogContext);
                }
              });
    } catch (TransactionFailedException e) {
      // The work's own exception, such as a refused write, passes on as it was thrown.
      if (e.getCause() instanceof RuntimeException cause
          && !(cause instanceof CouchbaseException)) {
        throw cause;
      }
      throw e;
    }
    return result.get();
  }

  private static void setLogContext(Map<String, String> logContext) {
    if (logContext == null) {
      MDC.clear();
    } else {
      MDC.setContextMap(logContext);
    }
  }

  /** The attempt running on this thread; empty outside {@link #inTransaction}. */
  Optional<TransactionAttemptContext> active() {
    return Optional.ofNullable(attempt.get());
  }

  /**
   * The attempt running on this thread, which a write requires; {@code what} names the write in the
   * exception when there is none, such as "Store a Product".
   */
  TransactionAttemptContext required(String what) {
    return active()
        .orElseThrow(
            () ->
                new IllegalStateException(
                    what
                        + " only inside Transactions.inTransaction, with the change it belongs to"));
  }

  @Configuration
  static class Durability {

    /**
     * How durably a transaction's writes are stored before it commits. A cluster of one node, as
     * the compose stack's, has no replicas to write to and needs {@code NONE}; a production cluster
     * keeps the SDK's {@code MAJORITY}.
     */
    @Bean
    ClusterEnvironmentBuilderCustomizer transactionDurability(
        @Value("${ecomm.catalog.transaction-durability:MAJORITY}") DurabilityLevel durability) {
      return environment ->
          environment.transactionsConfig(TransactionsConfig.durabilityLevel(durability));
    }
  }
}
