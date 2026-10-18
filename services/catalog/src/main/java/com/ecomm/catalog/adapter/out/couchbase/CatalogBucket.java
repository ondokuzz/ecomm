package com.ecomm.catalog.adapter.out.couchbase;

import com.couchbase.client.java.Bucket;
import com.couchbase.client.java.Cluster;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The Catalog's bucket, opened once on startup and waited for until it is ready, for {@code
 * ecomm.catalog.bucket-ready-timeout}. Every Couchbase adapter shares it.
 */
@Configuration(proxyBeanMethods = false)
class CatalogBucket {

  @Bean
  Bucket bucket(
      Cluster cluster,
      @Value("${ecomm.catalog.bucket}") String bucketName,
      @Value("${ecomm.catalog.bucket-ready-timeout}") Duration readyTimeout) {
    return open(cluster, bucketName, readyTimeout);
  }

  /**
   * The named bucket, once it is ready; an {@code UnambiguousTimeoutException} when it isn't within
   * {@code readyTimeout}.
   */
  static Bucket open(Cluster cluster, String bucketName, Duration readyTimeout) {
    var bucket = cluster.bucket(bucketName);
    bucket.waitUntilReady(readyTimeout);
    return bucket;
  }
}
