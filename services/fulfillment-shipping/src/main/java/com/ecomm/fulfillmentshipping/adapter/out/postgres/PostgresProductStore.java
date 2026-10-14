package com.ecomm.fulfillmentshipping.adapter.out.postgres;

import com.ecomm.fulfillmentshipping.application.port.out.ProductStore;
import com.ecomm.fulfillmentshipping.domain.Product;
import com.ecomm.fulfillmentshipping.domain.SerialTracking;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** The Product projection, as rows of {@code product} and {@code product_variant}. */
@Component
class PostgresProductStore implements ProductStore {

  private final JdbcClient jdbc;

  PostgresProductStore(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public Optional<Long> versionOf(String sku) {
    return jdbc.sql("SELECT version FROM product WHERE sku = :sku")
        .param("sku", sku)
        .query(Long.class)
        .optional();
  }

  @Override
  public void save(Product product) {
    jdbc.sql(
            """
            INSERT INTO product (sku, version, name, serial_tracking)
            VALUES (:sku, :version, :name, :serialTracking)
            ON CONFLICT (sku) DO UPDATE SET
              version = EXCLUDED.version, name = EXCLUDED.name,
              serial_tracking = EXCLUDED.serial_tracking
            """)
        .param("sku", product.sku())
        .param("version", product.version())
        .param("name", product.name())
        .param("serialTracking", product.serialTracking().name())
        .update();
    for (var variantId : product.variantIds()) {
      jdbc.sql(
              """
              INSERT INTO product_variant (variant_id, sku) VALUES (:variantId, :sku)
              ON CONFLICT (variant_id) DO UPDATE SET sku = EXCLUDED.sku
              """)
          .param("variantId", variantId)
          .param("sku", product.sku())
          .update();
    }
  }

  @Override
  public Map<String, Product> productsOf(Collection<String> variantIds) {
    var found = new HashMap<String, Product>();
    if (variantIds.isEmpty()) {
      return found;
    }
    jdbc.sql(
            """
            SELECT v.variant_id, p.sku, p.version, p.name, p.serial_tracking
            FROM product_variant v JOIN product p ON p.sku = v.sku
            WHERE v.variant_id IN (:variantIds)
            """)
        .param("variantIds", variantIds)
        .query(
            (rs, row) -> {
              found.put(
                  rs.getString("variant_id"),
                  new Product(
                      rs.getString("sku"),
                      rs.getLong("version"),
                      rs.getString("name"),
                      SerialTracking.valueOf(rs.getString("serial_tracking")),
                      Set.of()));
              return null;
            })
        .list();
    return found;
  }
}
