package com.ecomm.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.annotation.DirtiesContext;

/**
 * The Stock cache's counters are on {@code /actuator/prometheus} from the start, before anything is
 * counted, so that Prometheus has a sample before a counter's first increment and Grafana's {@code
 * increase()} counts it: the first Redis error after Inventory starts, say. The context is fresh,
 * and Redis is up, so nothing has failed yet.
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class StockCacheMetricsApiTest extends InventoryApiTest {

  @Test
  void theStockCacheCountersAreReportedBeforeTheirFirstCount() {
    var scrape =
        http.get()
            .uri("/actuator/prometheus")
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(String.class)
            .returnResult()
            .getResponseBody();

    for (var counter :
        List.of(
            "ecomm_stock_cache_hits_total",
            "ecomm_stock_cache_misses_total",
            "ecomm_stock_cache_errors_total")) {
      assertThat(scrape.lines().filter(line -> line.startsWith(counter + "{")))
          .as(counter)
          .hasSize(1);
    }
    assertThat(scrape).containsPattern("(?m)^ecomm_stock_cache_errors_total\\{[^}]*\\} 0\\.0$");
  }
}
