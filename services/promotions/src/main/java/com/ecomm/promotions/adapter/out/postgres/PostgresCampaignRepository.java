package com.ecomm.promotions.adapter.out.postgres;

import com.ecomm.promotions.application.port.out.CampaignRepository;
import com.ecomm.promotions.domain.Campaign;
import com.ecomm.promotions.domain.CampaignNotFoundException;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Campaigns as rows of the {@code campaign} table (see {@code db/migration}). */
@Component
class PostgresCampaignRepository implements CampaignRepository {

  private static final String COLUMNS =
      """
      id, name, discount_type, percent_off, amount_off_minor, amount_off_currency, categories,
      minimum_subtotal_minor, minimum_subtotal_currency, valid_from, valid_until, active, priority
      """;

  private static final RowMapper<Campaign> CAMPAIGN =
      (rs, row) ->
          new Campaign(
              rs.getObject("id", UUID.class),
              rs.getString("name"),
              TermColumns.discount(rs),
              List.of((String[]) rs.getArray("categories").getArray()),
              TermColumns.minimumSubtotal(rs),
              rs.getTimestamp("valid_from").toInstant(),
              rs.getTimestamp("valid_until").toInstant(),
              rs.getBoolean("active"),
              rs.getInt("priority"));

  private final JdbcClient jdbc;

  PostgresCampaignRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public boolean add(Campaign campaign) {
    try {
      jdbc.sql(
              "INSERT INTO campaign ("
                  + COLUMNS
                  + """
                  ) VALUES (
                    :id, :name, :discountType, :percentOff, :amountOffMinor, :amountOffCurrency,
                    :categories, :minimumSubtotalMinor, :minimumSubtotalCurrency, :validFrom,
                    :validUntil, :active, :priority)
                  """)
          .params(params(campaign))
          .update();
      return true;
    } catch (DuplicateKeyException e) {
      return false;
    }
  }

  @Override
  public boolean replace(Campaign campaign) {
    int updated;
    try {
      updated =
          jdbc.sql(
                  """
                  UPDATE campaign SET
                    name = :name, discount_type = :discountType, percent_off = :percentOff,
                    amount_off_minor = :amountOffMinor, amount_off_currency = :amountOffCurrency,
                    categories = :categories, minimum_subtotal_minor = :minimumSubtotalMinor,
                    minimum_subtotal_currency = :minimumSubtotalCurrency,
                    valid_from = :validFrom, valid_until = :validUntil, active = :active,
                    priority = :priority
                  WHERE id = :id
                  """)
              .params(params(campaign))
              .update();
    } catch (DuplicateKeyException e) {
      return false;
    }
    if (updated == 0) {
      throw new CampaignNotFoundException(campaign.id().toString());
    }
    return true;
  }

  @Override
  public boolean remove(UUID id) {
    return jdbc.sql("DELETE FROM campaign WHERE id = :id").param("id", id).update() == 1;
  }

  @Override
  public Optional<Campaign> find(UUID id) {
    return jdbc.sql("SELECT " + COLUMNS + " FROM campaign WHERE id = :id")
        .param("id", id)
        .query(CAMPAIGN)
        .optional();
  }

  @Override
  public Optional<Campaign> withPriority(int priority) {
    return jdbc.sql("SELECT " + COLUMNS + " FROM campaign WHERE priority = :priority")
        .param("priority", priority)
        .query(CAMPAIGN)
        .optional();
  }

  @Override
  public List<Campaign> all() {
    return jdbc.sql("SELECT " + COLUMNS + " FROM campaign ORDER BY priority")
        .query(CAMPAIGN)
        .list();
  }

  private static Map<String, Object> params(Campaign campaign) {
    var params = new HashMap<String, Object>();
    params.put("id", campaign.id());
    params.put("name", campaign.name());
    params.put("categories", campaign.categories().toArray(String[]::new));
    TermColumns.put(params, campaign.discount(), campaign.minimumSubtotal());
    params.put("validFrom", Timestamp.from(campaign.validFrom()));
    params.put("validUntil", Timestamp.from(campaign.validUntil()));
    params.put("active", campaign.active());
    params.put("priority", campaign.priority());
    return params;
  }
}
