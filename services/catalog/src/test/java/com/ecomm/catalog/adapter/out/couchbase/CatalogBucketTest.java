package com.ecomm.catalog.adapter.out.couchbase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.couchbase.client.core.error.UnambiguousTimeoutException;
import com.couchbase.client.java.Cluster;
import java.time.Duration;
import org.junit.jupiter.api.Test;

/** Opening the Catalog's bucket waits for it as long as the setting says, and no longer. */
class CatalogBucketTest {

  @Test
  void anUnreachableClusterIsGivenUpOnAfterTheReadyTimeout() {
    try (var cluster = Cluster.connect("couchbase://127.0.0.1:1", "admin", "unused")) {
      var started = System.nanoTime();

      assertThatThrownBy(() -> CatalogBucket.open(cluster, "catalog", Duration.ofSeconds(1)))
          .isInstanceOf(UnambiguousTimeoutException.class);

      assertThat(Duration.ofNanos(System.nanoTime() - started))
          .isBetween(Duration.ofMillis(900), Duration.ofSeconds(30));
    }
  }
}
