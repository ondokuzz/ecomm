package com.ecomm.promotions.adapter.in.web;

import com.ecomm.promotions.application.port.in.CampaignView;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A Campaign as Staff read it, with its {@code state} now: {@code running}, {@code scheduled},
 * {@code over} or {@code off}. {@code minimumSubtotal} is null when it has none, and {@code
 * categories} empty when it applies to every line.
 */
record CampaignResponse(
    UUID id,
    String name,
    DiscountRuleBody discount,
    List<String> categories,
    Amount minimumSubtotal,
    Instant validFrom,
    Instant validUntil,
    boolean active,
    int priority,
    String state) {

  static CampaignResponse of(CampaignView view) {
    var campaign = view.campaign();
    return new CampaignResponse(
        campaign.id(),
        campaign.name(),
        DiscountRuleBody.of(campaign.discount()),
        campaign.categories(),
        Amount.of(campaign.minimumSubtotal()),
        campaign.validFrom(),
        campaign.validUntil(),
        campaign.active(),
        campaign.priority(),
        view.state().label());
  }
}
