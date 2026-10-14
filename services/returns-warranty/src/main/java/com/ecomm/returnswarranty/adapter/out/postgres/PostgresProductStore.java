package com.ecomm.returnswarranty.adapter.out.postgres;

import com.ecomm.returnswarranty.application.port.out.ProductStore;
import com.ecomm.returnswarranty.domain.Product;
import com.ecomm.returnswarranty.domain.SerialTracking;
import com.ecomm.returnswarranty.domain.WarrantyTerms;
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
    var warranty = product.warrantyTerms();
    jdbc.sql(
            """
            INSERT INTO product
              (sku, version, serial_tracking, warranty_months, warranty_manufacturer)
            VALUES (:sku, :version, :serialTracking, :months, :manufacturer)
            ON CONFLICT (sku) DO UPDATE SET
              version = EXCLUDED.version, serial_tracking = EXCLUDED.serial_tracking,
              warranty_months = EXCLUDED.warranty_months,
              warranty_manufacturer = EXCLUDED.warranty_manufacturer
            """)
        .param("sku", product.sku())
        .param("version", product.version())
        .param("serialTracking", product.serialTracking().name())
        .param("months", warranty.map(WarrantyTerms::months).orElse(null))
        .param("manufacturer", warranty.map(WarrantyTerms::manufacturer).orElse(null))
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
  public Optional<Product> productOf(String variantId) {
    return jdbc.sql(
            """
            SELECT p.sku, p.version, p.serial_tracking, p.warranty_months, p.warranty_manufacturer
            FROM product_variant v JOIN product p ON p.sku = v.sku
            WHERE v.variant_id = :variantId
            """)
        .param("variantId", variantId)
        .query(
            (rs, row) -> {
              var months = rs.getObject("warranty_months", Integer.class);
              return new Product(
                  rs.getString("sku"),
                  rs.getLong("version"),
                  SerialTracking.valueOf(rs.getString("serial_tracking")),
                  months == null
                      ? null
                      : new WarrantyTerms(months, rs.getString("warranty_manufacturer")),
                  Set.of(variantId));
            })
        .optional();
  }
}
