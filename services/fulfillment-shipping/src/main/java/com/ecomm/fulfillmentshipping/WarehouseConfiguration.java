package com.ecomm.fulfillmentshipping;

import com.ecomm.fulfillmentshipping.domain.Warehouse;
import com.ecomm.fulfillmentshipping.domain.Warehouses;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The Warehouses, from {@code ecomm.fulfillment-shipping.warehouses}. The service doesn't start
 * unless they make a valid set (see {@link Warehouses}).
 */
@Configuration
@EnableConfigurationProperties(WarehouseConfiguration.WarehouseProperties.class)
class WarehouseConfiguration {

  @ConfigurationProperties("ecomm.fulfillment-shipping")
  record WarehouseProperties(List<Warehouse> warehouses) {}

  @Bean
  Warehouses warehouses(WarehouseProperties properties) {
    return new Warehouses(properties.warehouses());
  }
}
