package com.ecomm.catalog;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.context.ApplicationContext;

/** Seed data goes only into an empty bucket, so a restart never undoes Staff changes. */
class SeedDataApiTest extends CatalogApiTest {

  @Autowired ApplicationContext context;

  @Test
  void restartingDoesNotBringBackASeededProductStaffDeleted() throws Exception {
    http.delete()
        .uri("/products/AUD-SONOS-ERA-100")
        .headers(h -> h.setBearerAuth(staffToken()))
        .exchange()
        .expectStatus()
        .isNoContent();

    for (var runner : context.getBeansOfType(ApplicationRunner.class).values()) {
      runner.run(new DefaultApplicationArguments());
    }

    http.get().uri("/products/AUD-SONOS-ERA-100").exchange().expectStatus().isNotFound();
  }
}
