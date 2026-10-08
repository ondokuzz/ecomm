package com.ecomm.payment;

import com.ecomm.commons.events.IntegrationEventPublisher;
import com.ecomm.payment.application.PaymentService;
import com.ecomm.payment.application.port.out.PaymentGatewayPort;
import com.ecomm.payment.application.port.out.PaymentRepository;
import com.ecomm.payment.application.port.out.TimeSource;
import com.ecomm.payment.application.port.out.Transactions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires use cases as beans, so the application layer stays free of Spring annotations. Adapters are
 * ordinary Spring components.
 */
@Configuration
class UseCaseConfiguration {

  /** Serves every Payment use case. */
  @Bean
  PaymentService paymentService(
      PaymentGatewayPort gateway,
      PaymentRepository payments,
      Transactions transactions,
      IntegrationEventPublisher events,
      TimeSource time) {
    return new PaymentService(gateway, payments, transactions, events, time);
  }
}
