package com.ecomm.payment.adapter.out.gateway;

import com.ecomm.payment.application.port.out.PaymentGatewayPort;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Picks the {@link PaymentGatewayPort} adapter by {@code payment.gateway}, {@code mock} unless set.
 * A value no adapter answers to fails the binding, so the service never starts without a gateway.
 */
@Configuration
@EnableConfigurationProperties(PaymentGatewayConfiguration.GatewayProperties.class)
class PaymentGatewayConfiguration {

  /** The gateways Payment has an adapter for. */
  enum Gateway {
    MOCK
  }

  @ConfigurationProperties("payment")
  record GatewayProperties(@DefaultValue("mock") Gateway gateway) {}

  @Bean
  PaymentGatewayPort paymentGateway(GatewayProperties properties) {
    return switch (properties.gateway()) {
      case MOCK -> new MockPaymentGatewayAdapter();
    };
  }
}
