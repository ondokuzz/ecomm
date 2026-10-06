package com.ecomm.commons.events.outbox;

import com.ecomm.commons.events.IntegrationEventPublisher;
import java.time.Clock;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.modulith.events.EventExternalizationConfiguration;
import org.springframework.modulith.events.FailedEventPublications;
import org.springframework.modulith.events.RoutingTarget;
import tools.jackson.databind.json.JsonMapper;

/**
 * Publishing integration events through Spring Modulith's event publication registry and its Kafka
 * externalization, for a service that has both on its classpath (see {@link
 * OutboxIntegrationEventPublisher}). Each {@link OutboxedEvent} goes to its topic, keyed by its
 * aggregate's ID, with the Correlation ID as a header. Sends go out one at a time, each waiting for
 * Kafka to acknowledge the one before.
 */
@AutoConfiguration(
    afterName = {
      "org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration",
      "org.springframework.modulith.events.config.EventPublicationAutoConfiguration"
    })
@ConditionalOnClass({EventExternalizationConfiguration.class, KafkaOperations.class})
@EnableConfigurationProperties(OutboxProperties.class)
public class OutboxAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  IntegrationEventPublisher integrationEventPublisher(
      ApplicationEventPublisher events, JsonMapper json, ObjectProvider<Clock> clock) {
    return new OutboxIntegrationEventPublisher(
        events, json, clock.getIfAvailable(Clock::systemUTC));
  }

  @Bean
  @ConditionalOnMissingBean
  EventExternalizationConfiguration integrationEventExternalization() {
    return EventExternalizationConfiguration.externalizing()
        .selectByType(OutboxedEvent.class)
        .mapping(OutboxedEvent.class, OutboxedEvent::toMessage)
        .route(OutboxedEvent.class, e -> RoutingTarget.forTarget(e.topic()).andKey(e.key()))
        .serializeExternalization(true)
        .build();
  }

  @Bean
  @ConditionalOnBean(FailedEventPublications.class)
  FailedPublicationResubmitter failedPublicationResubmitter(
      FailedEventPublications failed, OutboxProperties properties) {
    return new FailedPublicationResubmitter(failed, properties.resubmitFailedEvery());
  }
}
