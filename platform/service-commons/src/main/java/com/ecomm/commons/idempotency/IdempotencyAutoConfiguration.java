package com.ecomm.commons.idempotency;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Idempotency keys for a Postgres service's {@link IdempotentCommand}s, kept in the {@code
 * idempotency_key} table its own migration adds. The filter runs after the security filter chain,
 * at the default order.
 */
@AutoConfiguration(
    afterName = {
      "org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration",
      "org.springframework.boot.jdbc.autoconfigure.JdbcTemplateAutoConfiguration"
    })
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass({JdbcTemplate.class, JwtAuthenticationToken.class})
@ConditionalOnBean({JdbcTemplate.class, PlatformTransactionManager.class})
@EnableConfigurationProperties(IdempotencyProperties.class)
@Import(IdempotencyProblemDetailHandler.class)
public class IdempotencyAutoConfiguration {

  @Bean
  IdempotencyKeys idempotencyKeys(JdbcTemplate jdbc) {
    return new IdempotencyKeys(jdbc);
  }

  @Bean
  FilterRegistrationBean<IdempotencyFilter> idempotencyFilter(
      @Qualifier("requestMappingHandlerMapping")
          ObjectProvider<RequestMappingHandlerMapping> handlerMapping,
      IdempotencyKeys keys,
      PlatformTransactionManager transactionManager,
      @Qualifier("handlerExceptionResolver") HandlerExceptionResolver problems) {
    return new FilterRegistrationBean<>(
        new IdempotencyFilter(handlerMapping, keys, transactionManager, problems));
  }

  @Bean
  IdempotencyKeyPruner idempotencyKeyPruner(
      IdempotencyKeys keys,
      IdempotencyProperties properties,
      @Qualifier("requestMappingHandlerMapping")
          ObjectProvider<RequestMappingHandlerMapping> handlerMapping) {
    return new IdempotencyKeyPruner(keys, properties, handlerMapping);
  }
}
