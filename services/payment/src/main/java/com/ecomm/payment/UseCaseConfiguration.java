package com.ecomm.payment;

import com.ecomm.payment.application.PaymentService;
import com.ecomm.payment.application.port.out.PaymentGatewayPort;
import com.ecomm.payment.application.port.out.PaymentRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires use cases as beans, so the application layer stays free of Spring annotations. Adapters are
 * ordinary Spring components.
 */
@Configuration
class UseCaseConfiguration {

  /** Serves every Payment use case: authorizing a payment and reading one back. */
  @Bean
  PaymentService paymentService(PaymentGatewayPort gateway, PaymentRepository payments) {
    return new PaymentService(gateway, payments);
  }
}
