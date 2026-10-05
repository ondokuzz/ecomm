package com.ecomm.catalog;

import com.couchbase.client.java.Cluster;
import com.ecomm.commons.events.EventBackbone;
import com.ecomm.commons.security.FakeKeycloak;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.testcontainers.couchbase.BucketDefinition;
import org.testcontainers.couchbase.CouchbaseContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Base for HTTP-seam tests: the app runs against one Couchbase container shared by every test
 * class, seeded once on first start, and the shared Kafka and schema registry from {@link
 * EventBackbone}, with Catalog's topics created and their schemas registered as the stack does.
 * Tests that change Products use SKUs of their own.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    // An unreachable topic fails a send in seconds rather than a minute.
    properties = "spring.kafka.producer.properties[max.block.ms]=2000")
@AutoConfigureRestTestClient
abstract class CatalogApiTest {

  private static final CouchbaseContainer COUCHBASE =
      new CouchbaseContainer(
              DockerImageName.parse("couchbase:community-7.6.2")
                  .asCompatibleSubstituteFor("couchbase/server"))
          .withBucket(new BucketDefinition("catalog").withPrimaryIndex(false))
          .withBucket(new BucketDefinition(BackfillApiTest.BUCKET).withPrimaryIndex(false));

  /** The topics Product and Category events are published to. */
  static final String PRODUCT_TOPIC = "catalog.product";

  static final String CATEGORY_TOPIC = "catalog.category";

  static {
    COUCHBASE.start();
    for (var topic : new String[] {PRODUCT_TOPIC, CATEGORY_TOPIC}) {
      EventBackbone.createTopic(topic);
      EventBackbone.registerSchemaOf(topic);
    }
  }

  /** Connects to the shared container, for setting up data as an older Catalog stored it. */
  static Cluster connect() {
    return Cluster.connect(
        COUCHBASE.getConnectionString(), COUCHBASE.getUsername(), COUCHBASE.getPassword());
  }

  @DynamicPropertySource
  static void infrastructure(DynamicPropertyRegistry registry) {
    FakeKeycloak.registerWith(registry);
    EventBackbone.registerWith(registry);
    registry.add("spring.couchbase.connection-string", COUCHBASE::getConnectionString);
    registry.add("spring.couchbase.username", COUCHBASE::getUsername);
    registry.add("spring.couchbase.password", COUCHBASE::getPassword);
  }

  @Autowired RestTestClient http;

  static String staffToken() {
    return FakeKeycloak.token("staff-7", "STAFF");
  }

  static String customerToken() {
    return FakeKeycloak.token("customer-42", "CUSTOMER");
  }
}
