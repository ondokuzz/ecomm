package com.ecomm.commons.metrics;

import java.util.Map;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.DefaultPropertiesPropertySource;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * What every service exports for Prometheus, below anything a service configures itself:
 *
 * <ul>
 *   <li>the actuator serves {@code /actuator/health} and {@code /actuator/prometheus}, both open
 *       without a token; the gateway routes neither;
 *   <li>every metric carries the service's {@code spring.application.name} as its {@code service}
 *       tag;
 *   <li>HTTP request timings are published as histograms, so Prometheus can work out percentiles
 *       across instances.
 * </ul>
 *
 * The Kafka consumer and outbox metrics come from {@code EventConsumerAutoConfiguration} and {@code
 * OutboxAutoConfiguration}.
 */
class MetricsDefaults implements EnvironmentPostProcessor {

  @Override
  public void postProcessEnvironment(
      ConfigurableEnvironment environment, SpringApplication application) {
    DefaultPropertiesPropertySource.addOrMerge(
        Map.of(
            "management.endpoints.web.exposure.include",
            "health,prometheus",
            "management.metrics.tags.service",
            "${spring.application.name}",
            "management.metrics.distribution.percentiles-histogram.http.server.requests",
            "true"),
        environment.getPropertySources());
  }
}
