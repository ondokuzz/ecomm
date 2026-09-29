package com.ecomm.inventory.adapter.out.postgres;

import com.ecomm.inventory.application.port.out.Transactions;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** A Postgres transaction around the work; any exception it throws rolls it back. */
@Component
class SpringTransactions implements Transactions {

  private final TransactionTemplate transactionTemplate;
  private final TransactionTemplate snapshotTemplate;

  SpringTransactions(
      TransactionTemplate transactionTemplate, PlatformTransactionManager transactionManager) {
    this.transactionTemplate = transactionTemplate;
    // In Postgres, REPEATABLE READ takes one snapshot at the first statement and keeps it.
    this.snapshotTemplate = new TransactionTemplate(transactionManager);
    snapshotTemplate.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    snapshotTemplate.setReadOnly(true);
  }

  @Override
  public <T> T inTransaction(Supplier<T> work) {
    return transactionTemplate.execute(status -> work.get());
  }

  @Override
  public <T> T inSnapshot(Supplier<T> work) {
    return snapshotTemplate.execute(status -> work.get());
  }
}
