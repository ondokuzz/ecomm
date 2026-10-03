package com.ecomm.promotions.application.port.in;

import com.ecomm.promotions.domain.Campaign;
import com.ecomm.promotions.domain.CampaignState;

/** A Campaign with where it stands now. */
public record CampaignView(Campaign campaign, CampaignState state) {}
