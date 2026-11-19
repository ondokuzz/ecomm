package com.ecomm.catalog;

import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.core.env.MapPropertySource;

/**
 * Seeds the Catalog as it would seed itself on start, then exits: the seed Categories and Products
 * into an empty bucket, or the seed's Warranty terms to seeded Products stored before them. In
 * Kubernetes, Catalog's two replicas start with {@code ecomm.catalog.seed.on-start} false, so they
 * don't both seed, and the catalog-seed Job runs this once in Catalog's image:
 *
 * <pre>java -Dloader.main=com.ecomm.catalog.CatalogSeed
 *     org.springframework.boot.loader.launch.PropertiesLauncher</pre>
 *
 * <p>It runs Catalog without its web server. The events it writes to the Outbox are sent by
 * whichever relay runs first, its own before it exits or the replicas'.
 */
public final class CatalogSeed {

  private CatalogSeed() {}

  public static void main(String[] args) {
    System.exit(seed(args));
  }

  /**
   * Starts Catalog with seeding on, which seeds as it starts, and closes it again.
   *
   * @return the exit code: 0 once seeded, or what Catalog's exit code generators say
   */
  public static int seed(String... args) {
    var catalog =
        new SpringApplicationBuilder(CatalogApplication.class)
            .web(WebApplicationType.NONE)
            // Over any argument or variable that turns it off, such as the replicas' own.
            .initializers(
                context ->
                    context
                        .getEnvironment()
                        .getPropertySources()
                        .addFirst(
                            new MapPropertySource(
                                "catalogSeed", Map.of("ecomm.catalog.seed.on-start", "true"))))
            .run(args);
    return SpringApplication.exit(catalog);
  }
}
