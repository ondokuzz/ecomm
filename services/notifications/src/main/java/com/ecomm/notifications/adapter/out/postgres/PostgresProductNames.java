package com.ecomm.notifications.adapter.out.postgres;

import com.ecomm.notifications.application.port.out.ProductNames;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Products as rows of {@code product}, with every Variant they have had in {@code product_variant},
 * each with its Price and when that last changed. A Variant's first Price, and the first one a
 * Variant projected before Prices were kept is given, is no change.
 */
@Component
class PostgresProductNames implements ProductNames {

  private final JdbcClient jdbc;

  PostgresProductNames(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public boolean anyWithoutPrice() {
    return jdbc.sql("SELECT EXISTS (SELECT 1 FROM product_variant WHERE price_minor IS NULL)")
        .query(Boolean.class)
        .single();
  }

  @Override
  public boolean withoutPrice(String sku) {
    return jdbc.sql(
            "SELECT EXISTS (SELECT 1 FROM product_variant WHERE sku = :sku AND price_minor IS NULL)")
        .param("sku", sku)
        .query(Boolean.class)
        .single();
  }

  /** Locks the row, if there is one, until the transaction ends. */
  @Override
  public Optional<Long> versionOf(String sku) {
    return jdbc.sql("SELECT version FROM product WHERE sku = :sku FOR UPDATE")
        .param("sku", sku)
        .query(Long.class)
        .optional();
  }

  @Override
  public void save(
      String sku,
      long version,
      String name,
      Map<String, VariantPrice> variants,
      Instant publishedAt) {
    jdbc.sql(
            """
            INSERT INTO product (sku, version, name) VALUES (:sku, :version, :name)
            ON CONFLICT (sku) DO UPDATE SET version = excluded.version, name = excluded.name
            """)
        .param("sku", sku)
        .param("version", version)
        .param("name", name)
        .update();
    variants.forEach(
        (variantId, variant) ->
            jdbc.sql(
                    """
                    INSERT INTO product_variant (variant_id, sku, label, price_minor, price_currency)
                    VALUES (:variantId, :sku, :label, :priceMinor, :priceCurrency)
                    ON CONFLICT (variant_id) DO UPDATE SET sku = excluded.sku,
                      label = excluded.label,
                      price_changed_at = CASE
                        WHEN product_variant.price_minor IS NOT NULL
                          AND (product_variant.price_minor, product_variant.price_currency)
                            IS DISTINCT FROM (excluded.price_minor, excluded.price_currency)
                        THEN :publishedAt ELSE product_variant.price_changed_at END,
                      price_minor = excluded.price_minor,
                      price_currency = excluded.price_currency
                    """)
                .param("variantId", variantId)
                .param("sku", sku)
                .param("label", variant.label())
                .param("priceMinor", variant.price().amountMinor())
                .param("priceCurrency", variant.price().currency().getCurrencyCode())
                .param("publishedAt", Timestamp.from(publishedAt))
                .update());
  }

  @Override
  public Map<String, ProjectedVariant> variantsOf(Collection<String> variantIds) {
    var variants = new HashMap<String, ProjectedVariant>();
    if (variantIds.isEmpty()) {
      return variants;
    }
    jdbc.sql(
            """
            SELECT v.variant_id, v.sku, p.name, v.label, v.price_changed_at
            FROM product_variant v JOIN product p USING (sku) WHERE v.variant_id IN (:variantIds)
            """)
        .param("variantIds", variantIds)
        .query(
            (rs, n) -> {
              var changedAt = rs.getTimestamp("price_changed_at");
              variants.put(
                  rs.getString("variant_id"),
                  new ProjectedVariant(
                      rs.getString("sku"),
                      nameOf(rs.getString("name"), rs.getString("label")),
                      changedAt == null ? null : changedAt.toInstant()));
              return null;
            })
        .list();
    return variants;
  }

  @Override
  public Map<String, String> namesOf(Collection<String> variantIds) {
    var names = new HashMap<String, String>();
    if (variantIds.isEmpty()) {
      return names;
    }
    jdbc.sql(
            """
            SELECT v.variant_id, p.name, v.label FROM product_variant v JOIN product p USING (sku)
            WHERE v.variant_id IN (:variantIds)
            """)
        .param("variantIds", variantIds)
        .query(
            (rs, n) -> {
              names.put(
                  rs.getString("variant_id"), nameOf(rs.getString("name"), rs.getString("label")));
              return null;
            })
        .list();
    return names;
  }

  /** Such as {@code Google Pixel 9 (128 GB)}, or the Product's name alone without axis values. */
  private static String nameOf(String name, String label) {
    return label.isEmpty() ? name : name + " (" + label + ")";
  }
}
