package com.ecomm.notifications.adapter.out.postgres;

import com.ecomm.notifications.application.port.out.ProductNames;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Products as rows of {@code product}, with every Variant they have had in {@code product_variant}.
 */
@Component
class PostgresProductNames implements ProductNames {

  private final JdbcClient jdbc;

  PostgresProductNames(JdbcClient jdbc) {
    this.jdbc = jdbc;
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
  public void save(String sku, long version, String name, Map<String, String> variantLabels) {
    jdbc.sql(
            """
            INSERT INTO product (sku, version, name) VALUES (:sku, :version, :name)
            ON CONFLICT (sku) DO UPDATE SET version = excluded.version, name = excluded.name
            """)
        .param("sku", sku)
        .param("version", version)
        .param("name", name)
        .update();
    variantLabels.forEach(
        (variantId, label) ->
            jdbc.sql(
                    """
                    INSERT INTO product_variant (variant_id, sku, label) VALUES (:variantId, :sku, :label)
                    ON CONFLICT (variant_id) DO UPDATE SET sku = excluded.sku, label = excluded.label
                    """)
                .param("variantId", variantId)
                .param("sku", sku)
                .param("label", label)
                .update());
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
              var label = rs.getString("label");
              var name = rs.getString("name");
              names.put(
                  rs.getString("variant_id"), label.isEmpty() ? name : name + " (" + label + ")");
              return null;
            })
        .list();
    return names;
  }
}
