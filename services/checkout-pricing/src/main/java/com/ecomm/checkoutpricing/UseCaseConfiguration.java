package com.ecomm.checkoutpricing;

import com.ecomm.checkoutpricing.application.CheckoutService;
import com.ecomm.checkoutpricing.application.port.out.CartPort;
import com.ecomm.checkoutpricing.application.port.out.CatalogPort;
import com.ecomm.checkoutpricing.application.port.out.CheckoutSessionRepository;
import com.ecomm.checkoutpricing.application.port.out.InventoryPort;
import com.ecomm.checkoutpricing.application.port.out.OrderPort;
import com.ecomm.checkoutpricing.application.port.out.PaymentPort;
import com.ecomm.checkoutpricing.application.port.out.PromotionsPort;
import com.ecomm.checkoutpricing.application.port.out.TaxCalculator;
import com.ecomm.checkoutpricing.application.port.out.TimeSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires use cases as beans, so the application layer stays free of Spring annotations. Adapters are
 * ordinary Spring components.
 */
@Configuration
class UseCaseConfiguration {

  @Bean
  CheckoutService checkoutService(
      CartPort cart,
      CatalogPort catalog,
      OrderPort orders,
      InventoryPort inventory,
      PaymentPort payments,
      PromotionsPort promotions,
      TaxCalculator taxes,
      CheckoutSessionRepository sessions,
      TimeSource time) {
    return new CheckoutService(
        cart, catalog, orders, inventory, payments, promotions, taxes, sessions, time);
  }
}
