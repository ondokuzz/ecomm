package com.ecomm.promotions.application.port.in;

import com.ecomm.promotions.domain.Campaign;
import com.ecomm.promotions.domain.CampaignNotFoundException;
import com.ecomm.promotions.domain.CatalogUnavailableException;
import com.ecomm.promotions.domain.InvalidPromotionException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Staff manage Campaigns. Each write is checked against Catalog: a Campaign's Categories must be
 * Catalog's, and its amounts in currencies Catalog prices in. Its priority must be its own.
 */
public interface ManageCampaignsUseCase {

  /**
   * Adds a Campaign with {@code campaign}'s fields, under a new ID.
   *
   * @throws InvalidPromotionException naming the field when a Category or currency isn't Catalog's,
   *     or another Campaign has the priority
   * @throws CatalogUnavailableException when Catalog can't say
   */
  CampaignView create(Campaign campaign);

  /**
   * Replaces every field of the Campaign with {@code campaign}'s ID. Only a Category or currency it
   * didn't already have is checked against Catalog, so one Catalog has since dropped doesn't stop
   * Staff switching it off.
   *
   * @throws CampaignNotFoundException when there is none
   * @throws InvalidPromotionException as for {@link #create}
   * @throws CatalogUnavailableException when Catalog can't say
   */
  CampaignView update(Campaign campaign);

  /**
   * @throws CampaignNotFoundException when there is none
   */
  void delete(UUID id);

  Optional<CampaignView> campaign(UUID id);

  /** Every Campaign, by priority. */
  List<CampaignView> campaigns();
}
