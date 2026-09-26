package com.ecomm.catalog;

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
 * class, seeded once on first start. Tests that change Products use SKUs of their own.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
abstract class CatalogApiTest {

  private static final CouchbaseContainer COUCHBASE =
      new CouchbaseContainer(
              DockerImageName.parse("couchbase:community-7.6.2")
                  .asCompatibleSubstituteFor("couchbase/server"))
          .withBucket(new BucketDefinition("catalog").withPrimaryIndex(false));

  static {
    COUCHBASE.start();
  }

  @DynamicPropertySource
  static void infrastructure(DynamicPropertyRegistry registry) {
    FakeKeycloak.registerWith(registry);
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
