package com.ecomm.promotions.application.port.out;

import com.ecomm.promotions.domain.Campaign;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Where Campaigns live, each under its ID, no two with the same priority. */
public interface CampaignRepository {

  /** Adds {@code campaign}; false, changing nothing, when another Campaign has its priority. */
  boolean add(Campaign campaign);

  /**
   * Replaces the Campaign with {@code campaign}'s ID; false, changing nothing, when another
   * Campaign has its priority.
   */
  boolean replace(Campaign campaign);

  /** Removes the Campaign with this ID; false when there is none. */
  boolean remove(UUID id);

  Optional<Campaign> find(UUID id);

  /** The Campaign with this priority, if any. */
  Optional<Campaign> withPriority(int priority);

  /** Every Campaign, by priority. */
  List<Campaign> all();
}
