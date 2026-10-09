package com.ecomm.payment.adapter.out.gateway;

import com.ecomm.payment.application.port.out.PaymentGatewayPort;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

/**
 * Picks the {@link PaymentGatewayPort} adapter by {@code payment.gateway}, {@code mock} unless set.
 * A value no adapter answers to fails the binding, so the service never starts without a gateway.
 * The mock signs its webhooks with {@code payment.webhooks.secret}, which has no default.
 */
@Configuration
@EnableConfigurationProperties(PaymentGatewayConfiguration.GatewayProperties.class)
class PaymentGatewayConfiguration {

  /** The gateways Payment has an adapter for. */
  enum Gateway {
    MOCK
  }

  /**
   * @param mockGateway how the mock sends its webhooks: {@code webhook-delay} after a pending
   *     answer (3 seconds unless set), to {@code webhook-url} (Payment's own endpoint unless set)
   * @param webhooks {@code secret}: what webhooks are signed with
   */
  @ConfigurationProperties("payment")
  record GatewayProperties(
      @DefaultValue("mock") Gateway gateway,
      @DefaultValue MockGateway mockGateway,
      @DefaultValue Webhooks webhooks) {

    record MockGateway(@DefaultValue("3s") Duration webhookDelay, String webhookUrl) {}

    record Webhooks(String secret) {}
  }

  @Bean
  WebhookDelivery httpWebhookDelivery(
      GatewayProperties properties, RestClient.Builder http, Environment environment) {
    return new HttpWebhookDelivery(
        http.build(), properties.mockGateway().webhookUrl(), environment);
  }

  @Bean
  PaymentGatewayPort paymentGateway(
      GatewayProperties properties, WebhookDelivery webhooks, JsonMapper json) {
    var secret = properties.webhooks().secret();
    if (secret == null || secret.isBlank()) {
      throw new IllegalStateException(
          "payment.webhooks.secret is required: set PAYMENT_WEBHOOK_SECRET");
    }
    return switch (properties.gateway()) {
      case MOCK ->
          new MockPaymentGatewayAdapter(
              webhooks, properties.mockGateway().webhookDelay(), secret, json);
    };
  }
}
