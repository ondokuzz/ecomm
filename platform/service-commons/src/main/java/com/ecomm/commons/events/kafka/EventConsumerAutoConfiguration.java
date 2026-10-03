package com.ecomm.commons.events.kafka;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.kafka.autoconfigure.ConcurrentKafkaListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.RecordInterceptor;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
import org.springframework.kafka.support.converter.StringJacksonJsonMessageConverter;
import tools.jackson.databind.json.JsonMapper;

/**
 * What every {@code @KafkaListener} gets, for a service with Kafka on its classpath:
 *
 * <ul>
 *   <li>its event as the listener method's parameter type, read tolerantly: unknown fields are
 *       ignored and nothing is validated against the schema;
 *   <li>the event's Correlation ID in the MDC (see {@link CorrelationIdRecordInterceptor});
 *   <li>a failure retried with backoff (see {@link ConsumerProperties}), then logged and skipped
 *       (see {@link SkippedEventLogger}). An event that can't be read at all is skipped at once.
 * </ul>
 *
 * Apply each event only if it is newer than what you hold, with {@code Versions.applyIfNewer}.
 */
@AutoConfiguration(
    beforeName = "org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration",
    afterName = "org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration")
@ConditionalOnClass(ConcurrentKafkaListenerContainerFactory.class)
@EnableConfigurationProperties(ConsumerProperties.class)
public class EventConsumerAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  RecordInterceptor<Object, Object> correlationIdRecordInterceptor() {
    return new CorrelationIdRecordInterceptor();
  }

  @Bean
  @ConditionalOnMissingBean
  CommonErrorHandler skipAfterRetriesErrorHandler(JsonMapper json, ConsumerProperties properties) {
    var backOff = new ExponentialBackOffWithMaxRetries(properties.retries());
    backOff.setInitialInterval(properties.initialBackoff().toMillis());
    backOff.setMultiplier(2);
    return new DefaultErrorHandler(new SkippedEventLogger(json), backOff);
  }

  /**
   * Boot's factory, with the interceptor and error handler above, converting each event's JSON to
   * the listener's parameter type. Replaces Boot's own, which would take a JSON converter from a
   * {@code RecordMessageConverter} bean that the producer would then use too, sending JSON strings
   * where the schema expects objects.
   */
  @Bean
  @ConditionalOnMissingBean(name = "kafkaListenerContainerFactory")
  ConcurrentKafkaListenerContainerFactory<Object, Object> kafkaListenerContainerFactory(
      ConcurrentKafkaListenerContainerFactoryConfigurer configurer,
      ConsumerFactory<Object, Object> consumerFactory,
      JsonMapper json) {
    var factory = new ConcurrentKafkaListenerContainerFactory<Object, Object>();
    configurer.configure(factory, consumerFactory);
    factory.setRecordMessageConverter(new StringJacksonJsonMessageConverter(json));
    return factory;
  }
}
