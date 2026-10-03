package com.ecomm.promotions.application;

import com.ecomm.promotions.application.port.in.CampaignView;
import com.ecomm.promotions.application.port.in.ManageCampaignsUseCase;
import com.ecomm.promotions.application.port.out.CampaignRepository;
import com.ecomm.promotions.application.port.out.CatalogPort;
import com.ecomm.promotions.application.port.out.TimeSource;
import com.ecomm.promotions.domain.Campaign;
import com.ecomm.promotions.domain.CampaignNotFoundException;
import com.ecomm.promotions.domain.InvalidPromotionException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

public class CampaignService implements ManageCampaignsUseCase {

  private final CampaignRepository campaigns;
  private final CatalogPort catalog;
  private final TimeSource time;

  public CampaignService(CampaignRepository campaigns, CatalogPort catalog, TimeSource time) {
    this.campaigns = campaigns;
    this.catalog = catalog;
    this.time = time;
  }

  @Override
  public CampaignView create(Campaign campaign) {
    checkPriorityIsFree(campaign);
    checkAgainstCatalog(campaign, null);
    if (!campaigns.add(campaign)) {
      throw priorityTaken(campaign.priority());
    }
    return view(campaign);
  }

  @Override
  public CampaignView update(Campaign campaign) {
    var existing =
        campaigns
            .find(campaign.id())
            .orElseThrow(() -> new CampaignNotFoundException(campaign.id().toString()));
    checkPriorityIsFree(campaign);
    checkAgainstCatalog(campaign, existing);
    if (!campaigns.replace(campaign)) {
      throw priorityTaken(campaign.priority());
    }
    return view(campaign);
  }

  @Override
  public void delete(UUID id) {
    if (!campaigns.remove(id)) {
      throw new CampaignNotFoundException(id.toString());
    }
  }

  @Override
  public Optional<CampaignView> campaign(UUID id) {
    return campaigns.find(id).map(this::view);
  }

  @Override
  public List<CampaignView> campaigns() {
    var now = time.now();
    return campaigns.all().stream().map(c -> new CampaignView(c, c.stateAt(now))).toList();
  }

  private CampaignView view(Campaign campaign) {
    return new CampaignView(campaign, campaign.stateAt(time.now()));
  }

  private void checkPriorityIsFree(Campaign campaign) {
    campaigns
        .withPriority(campaign.priority())
        .filter(other -> !other.id().equals(campaign.id()))
        .ifPresent(
            other -> {
              throw priorityTaken(campaign.priority());
            });
  }

  private static InvalidPromotionException priorityTaken(int priority) {
    return new InvalidPromotionException(
        "priority", "is taken: another Campaign has priority " + priority);
  }

  /**
   * Checks the Categories {@code campaign} has that {@code existing}, its stored self if any,
   * doesn't, and each currency that isn't the one {@code existing} has in the same field, asking
   * Catalog only when there are some.
   */
  private void checkAgainstCatalog(Campaign campaign, Campaign existing) {
    var categories = campaign.categories();
    Predicate<String> isNewCategory = c -> existing == null || !existing.categories().contains(c);
    if (categories.stream().anyMatch(isNewCategory)) {
      var known = catalog.categories();
      for (var i = 0; i < categories.size(); i++) {
        var category = categories.get(i);
        if (isNewCategory.test(category) && !known.contains(category)) {
          throw new InvalidPromotionException(
              "categories[" + i + "]", "is not a Category in Catalog: " + category);
        }
      }
    }
    var newCurrencies =
        campaign.currencies().entrySet().stream()
            .filter(
                e ->
                    existing == null || !e.getValue().equals(existing.currencies().get(e.getKey())))
            .toList();
    if (!newCurrencies.isEmpty()) {
      var known = catalog.currencies();
      for (var currency : newCurrencies) {
        if (!known.contains(currency.getValue())) {
          throw new InvalidPromotionException(
              currency.getKey(), "is not a currency Catalog prices in: " + currency.getValue());
        }
      }
    }
  }
}
