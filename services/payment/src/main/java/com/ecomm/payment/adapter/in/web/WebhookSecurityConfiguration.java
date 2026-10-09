package com.ecomm.payment.adapter.in.web;

import com.ecomm.commons.security.ResourceServerSecurity;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * service-commons' rules, plus {@code POST /webhooks/gateway} with no token: the gateway calls it
 * from outside, and the webhook's signature authenticates it instead.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(WebhookProperties.class)
class WebhookSecurityConfiguration {

  @Bean
  SecurityFilterChain securityFilterChain(
      HttpSecurity http,
      @Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver)
      throws Exception {
    return ResourceServerSecurity.configure(http, exceptionResolver)
        .authorizeHttpRequests(
            requests ->
                requests
                    .requestMatchers("/actuator/health/**", "/actuator/prometheus")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/webhooks/gateway")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .build();
  }
}
