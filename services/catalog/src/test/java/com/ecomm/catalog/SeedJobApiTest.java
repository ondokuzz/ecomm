package com.ecomm.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.test.context.TestPropertySource;

/**
 * In Kubernetes Catalog runs two replicas, which must not both seed an empty bucket, so they start
 * with seeding off and the catalog-seed Job seeds it (infra/k8s/base/init/catalog-seed.yaml). The
 * service runs against an empty bucket of its own here.
 */
@TestPropertySource(
    properties = {
      "ecomm.catalog.bucket=" + SeedJobApiTest.BUCKET,
      "ecomm.catalog.seed.on-start=false"
    })
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SeedJobApiTest extends CatalogApiTest {

  static final String BUCKET = "catalog-seed-job";

  @Autowired ConfigurableEnvironment environment;

  @Test
  @Order(1)
  void aCatalogStartedWithSeedingOffLeavesAnEmptyBucketEmpty() {
    http.get().uri("/categories").exchange().expectBody().jsonPath("$.length()").isEqualTo(0);
    http.get().uri("/products/LPT-XPS-13").exchange().expectStatus().isNotFound();
  }

  @Test
  @Order(2)
  void theSeedJobSeedsTheEmptyBucketAndExits() {
    assertThat(CatalogSeed.seed(testProperties())).isZero();

    http.get().uri("/categories").exchange().expectBody().jsonPath("$.length()").isEqualTo(3);
    http.get()
        .uri("/products/LPT-XPS-13")
        .exchange()
        .expectBody()
        .jsonPath("$.warranty.manufacturer")
        .isEqualTo("Dell");
  }

  /** The properties this test's Catalog runs with, as arguments for another run of it. */
  private String[] testProperties() {
    var args = new ArrayList<String>();
    for (var name : List.of("Inlined Test Properties", "Dynamic Test Properties")) {
      if (environment.getPropertySources().get(name)
          instanceof EnumerablePropertySource<?> source) {
        for (var property : source.getPropertyNames()) {
          args.add("--" + property + "=" + environment.getProperty(property));
        }
      }
    }
    return args.toArray(String[]::new);
  }
}
