package com.ecomm.promotions;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;

/** Staff create, read, change, list and delete Campaigns. */
class CampaignApiTest extends PromotionsApiTest {

  @Test
  void staffCreateACampaignAndReadItBack() {
    var id =
        createCampaign(
                """
                {"name": "  Spring sale  ", "discount": {"type": "AMOUNT_OFF",
                 "amountOff": {"amountMinor": 2500, "currency": "EUR"}},
                 "categories": ["phones", "audio"],
                 "minimumSubtotal": {"amountMinor": 10000, "currency": "EUR"},
                 "validFrom": "2020-03-01T00:00:00Z", "validUntil": "2100-06-01T00:00:00Z",
                 "active": true, "priority": 101}
                """)
            .expectStatus()
            .isCreated()
            .expectBody(CampaignId.class)
            .returnResult()
            .getResponseBody()
            .id();

    readCampaign(id)
        .expectStatus()
        .isOk()
        .expectBody()
        .json(
            """
            {"id": "%s", "name": "Spring sale",
             "discount": {"type": "AMOUNT_OFF", "percentOff": null,
                          "amountOff": {"amountMinor": 2500, "currency": "EUR"}},
             "categories": ["phones", "audio"],
             "minimumSubtotal": {"amountMinor": 10000, "currency": "EUR"},
             "validFrom": "2020-03-01T00:00:00Z", "validUntil": "2100-06-01T00:00:00Z",
             "active": true, "priority": 101, "state": "running"}
            """
                .formatted(id));
  }

  @Test
  void theNewCampaignsUrlIsInLocation() {
    var result =
        createCampaign(campaign("Located", 102, 10))
            .expectStatus()
            .isCreated()
            .expectBody(CampaignId.class)
            .returnResult();

    assertThat(result.getResponseHeaders().getLocation())
        .hasToString("/campaigns/" + result.getResponseBody().id());
  }

  @Test
  void aCampaignWithoutCategoriesOrMinimumHasNone() {
    var id =
        createdCampaign(
            """
            {"name": "Everything", "discount": {"type": "PERCENT_OFF", "percentOff": 5},
             "validFrom": "2020-01-01T00:00:00Z", "validUntil": "2100-01-01T00:00:00Z",
             "active": true, "priority": 103}
            """);

    readCampaign(id)
        .expectBody()
        .json(
            """
            {"categories": [], "minimumSubtotal": null,
             "discount": {"type": "PERCENT_OFF", "percentOff": 5, "amountOff": null}}
            """);
  }

  @Test
  void staffReplaceACampaign() {
    var id = createdCampaign(campaign("Before", 104, 10));

    updateCampaign(
            id,
            """
            {"name": "After", "discount": {"type": "AMOUNT_OFF",
             "amountOff": {"amountMinor": 1000, "currency": "JPY"}},
             "categories": ["audio"], "minimumSubtotal": {"amountMinor": 5000, "currency": "JPY"},
             "validFrom": "2030-01-01T00:00:00Z", "validUntil": "2031-01-01T00:00:00Z",
             "active": false, "priority": 105}
            """)
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.id")
        .isEqualTo(id);

    readCampaign(id)
        .expectBody()
        .json(
            """
            {"id": "%s", "name": "After",
             "discount": {"type": "AMOUNT_OFF", "amountOff": {"amountMinor": 1000, "currency": "JPY"}},
             "categories": ["audio"], "minimumSubtotal": {"amountMinor": 5000, "currency": "JPY"},
             "validFrom": "2030-01-01T00:00:00Z", "validUntil": "2031-01-01T00:00:00Z",
             "active": false, "priority": 105, "state": "off"}
            """
                .formatted(id));
  }

  @Test
  void aCampaignKeepsItsOwnPriorityWhenReplaced() {
    var id = createdCampaign(campaign("Same priority", 106, 10));

    updateCampaign(id, campaign("Same priority, renamed", 106, 20)).expectStatus().isOk();
  }

  @Test
  void staffDeleteACampaign() {
    var id = createdCampaign(campaign("Goodbye", 107, 10));

    deleteCampaign(id).expectStatus().isNoContent();

    readCampaign(id).expectStatus().isNotFound();
    deleteCampaign(id)
        .expectStatus()
        .isNotFound()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON);
  }

  @Test
  void anUnknownCampaignIsNotFound() {
    var unknown = "7f1c2a3b-0000-4000-8000-000000000999";
    readCampaign(unknown)
        .expectStatus()
        .isNotFound()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON);
    updateCampaign(unknown, campaign("Nobody", 108, 10)).expectStatus().isNotFound();
    readCampaign("not-a-uuid").expectStatus().isNotFound();
  }

  @Test
  void staffListEveryCampaignByPriority() {
    createdCampaign(campaign("List third", 111, 10));
    createdCampaign(campaign("List first", 109, 10));
    createdCampaign(campaign("List second", 110, 10));

    var listed =
        listCampaigns()
            .expectStatus()
            .isOk()
            .expectBody(new ParameterizedTypeReference<List<CampaignView>>() {})
            .returnResult()
            .getResponseBody();

    assertThat(listed).extracting(CampaignView::priority).isSorted();
    assertThat(listed)
        .extracting(CampaignView::name)
        .containsSubsequence("List first", "List second", "List third");
  }

  record CampaignView(String name, int priority) {}
}
