package com.ecomm.promotions;

import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * Each Campaign says whether it is running, scheduled, over or switched off at Promotions' clock,
 * which these tests move.
 */
class CampaignStateApiTest extends PromotionsApiTest {

  private static final String WINDOW =
      """
      {"name": "%s", "discount": {"type": "PERCENT_OFF", "percentOff": 10}, "categories": [],
       "validFrom": "2030-01-01T00:00:00Z", "validUntil": "2030-02-01T00:00:00Z",
       "active": %s, "priority": %d}
      """;

  @Test
  void anActiveCampaignIsScheduledThenRunningThenOver() {
    var id = createdCampaign(WINDOW.formatted("January 2030", true, 801));

    time.set(Instant.parse("2029-12-31T23:59:59Z"));
    expectState(id, "scheduled");

    time.set(Instant.parse("2030-01-01T00:00:00Z"));
    expectState(id, "running");

    time.set(Instant.parse("2030-01-31T23:59:59Z"));
    expectState(id, "running");

    time.set(Instant.parse("2030-02-01T00:00:00Z"));
    expectState(id, "over");
  }

  @Test
  void aSwitchedOffCampaignIsOffWhateverTheTime() {
    var id = createdCampaign(WINDOW.formatted("Switched off", false, 802));

    for (var at :
        new String[] {"2029-01-01T00:00:00Z", "2030-01-15T00:00:00Z", "2031-01-01T00:00:00Z"}) {
      time.set(Instant.parse(at));
      expectState(id, "off");
    }
  }

  @Test
  void theListGivesEachCampaignsState() {
    var id = createdCampaign(WINDOW.formatted("Listed with state", true, 803));
    time.set(Instant.parse("2030-01-15T00:00:00Z"));

    listCampaigns()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$[?(@.id == '%s')].state".formatted(id))
        .isEqualTo("running");
  }

  private void expectState(String id, String state) {
    readCampaign(id).expectStatus().isOk().expectBody().jsonPath("$.state").isEqualTo(state);
  }
}
