package com.ecomm.supportassistant.adapter.out.http;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * The {@link RestClient}s the tools read the contexts with. Each carries the Customer's own token
 * (ADR 0002) and, as every client built from Boot's builder does, the Correlation ID. A context
 * that doesn't connect or answer in time fails the lookup, not the turn.
 */
@Configuration
class HttpClientsConfiguration {

  @Bean
  RestClient orderManagementRestClient(
      RestClient.Builder builder,
      @Value("${ecomm.support-assistant.order-management-url}") String url,
      @Value("${ecomm.support-assistant.connect-timeout}") Duration connectTimeout,
      @Value("${ecomm.support-assistant.read-timeout}") Duration readTimeout) {
    return client(builder, url, connectTimeout, readTimeout);
  }

  @Bean
  RestClient fulfillmentShippingRestClient(
      RestClient.Builder builder,
      @Value("${ecomm.support-assistant.fulfillment-shipping-url}") String url,
      @Value("${ecomm.support-assistant.connect-timeout}") Duration connectTimeout,
      @Value("${ecomm.support-assistant.read-timeout}") Duration readTimeout) {
    return client(builder, url, connectTimeout, readTimeout);
  }

  @Bean
  RestClient paymentRestClient(
      RestClient.Builder builder,
      @Value("${ecomm.support-assistant.payment-url}") String url,
      @Value("${ecomm.support-assistant.connect-timeout}") Duration connectTimeout,
      @Value("${ecomm.support-assistant.read-timeout}") Duration readTimeout) {
    return client(builder, url, connectTimeout, readTimeout);
  }

  @Bean
  RestClient returnsWarrantyRestClient(
      RestClient.Builder builder,
      @Value("${ecomm.support-assistant.returns-warranty-url}") String url,
      @Value("${ecomm.support-assistant.connect-timeout}") Duration connectTimeout,
      @Value("${ecomm.support-assistant.read-timeout}") Duration readTimeout) {
    return client(builder, url, connectTimeout, readTimeout);
  }

  @Bean
  RestClient searchDiscoveryRestClient(
      RestClient.Builder builder,
      @Value("${ecomm.support-assistant.search-discovery-url}") String url,
      @Value("${ecomm.support-assistant.connect-timeout}") Duration connectTimeout,
      @Value("${ecomm.support-assistant.read-timeout}") Duration readTimeout) {
    return client(builder, url, connectTimeout, readTimeout);
  }

  private static RestClient client(
      RestClient.Builder builder, String url, Duration connectTimeout, Duration readTimeout) {
    var requests =
        new JdkClientHttpRequestFactory(
            HttpClient.newBuilder().connectTimeout(connectTimeout).build());
    requests.setReadTimeout(readTimeout);
    return builder
        .clone()
        .baseUrl(url)
        .requestFactory(requests)
        .requestInterceptor(new ForwardCustomerToken())
        .build();
  }
}
