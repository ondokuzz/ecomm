package com.ecomm.commons.events.kafka;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.config.MeterFilter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.kafka.autoconfigure.ConcurrentKafkaListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.annotation.KafkaListenerAnnotationBeanPostProcessor.AnnotationEnhancer;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.CompositeRecordInterceptor;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.ErrorHandlingUtils;
import org.springframework.kafka.listener.RecordInterceptor;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
import org.springframework.kafka.support.converter.StringJacksonJsonMessageConverter;
import org.springframework.util.backoff.BackOff;
import tools.jackson.databind.json.JsonMapper;

/**
 * What every {@code @KafkaListener} gets, for a service with Kafka on its classpath:
 *
 * <ul>
 *   <li>its event as the listener method's parameter type, read tolerantly: unknown fields are
 *       ignored and nothing is validated against the schema;
 *   <li>the event's Correlation ID in the MDC (see {@link CorrelationIdRecordInterceptor});
 *   <li>a failure retried with backoff (see {@link ConsumerProperties}), then sent to the consumer
 *       group's dead-letter topic (see {@link DeadLetterRecoverer}). An event that can't be read at
 *       all, malformed or missing a field its record marks {@code @JsonProperty(required = true)},
 *       goes there at once;
 *   <li>its consumer group's redrive topic consumed too (see {@link RedriveTopicEnhancer} and
 *       {@link DeadLetterRedrive});
 *   <li>its retries, dead-lettered and redriven events counted per group and topic (see {@link
 *       RetriedEventCounter}, {@link DeadLetterRecoverer} and {@link RedrivenEventCounter}), and
 *       its group tagged on Kafka's consumer metrics (see {@link GroupTaggedConsumerMetrics} and
 *       {@link LegacyTopicMetricsFilter}).
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

  /** Static, as it is used by the post-processor that reads the annotations. */
  @Bean
  static AnnotationEnhancer redriveTopicEnhancer() {
    return new RedriveTopicEnhancer();
  }

  @Bean
  @ConditionalOnMissingBean
  RecordInterceptor<Object, Object> recordInterceptor(ObjectProvider<MeterRegistry> registry) {
    var correlationId = new CorrelationIdRecordInterceptor();
    var meters = registry.getIfAvailable();
    return meters == null
        ? correlationId
        : new CompositeRecordInterceptor<>(correlationId, new RedrivenEventCounter(meters));
  }

  @Bean
  DeadLetterRecoverer deadLetterRecoverer(
      ProducerFactory<?, ?> producerFactory,
      JsonMapper json,
      ObjectProvider<MeterRegistry> registry) {
    return new DeadLetterRecoverer(producerFactory, json, registry.getIfAvailable());
  }

  @Bean
  @ConditionalOnMissingBean
  CommonErrorHandler deadLetterAfterRetriesErrorHandler(
      DeadLetterRecoverer recoverer,
      ConsumerProperties properties,
      ObjectProvider<MeterRegistry> registry) {
    var backOff = new ExponentialBackOffWithMaxRetries(properties.retries());
    backOff.setInitialInterval(properties.initialBackoff().toMillis());
    backOff.setMultiplier(2);
    var handler = new RetryClassifyingErrorHandler(recoverer, backOff);
    registry.ifAvailable(
        meters ->
            handler.setRetryListeners(
                new RetriedEventCounter(meters, properties.retries(), handler::isRetryable)));
    return handler;
  }

  /** Says whether it retries a failure, so that only retries are counted as such. */
  private static class RetryClassifyingErrorHandler extends DefaultErrorHandler {

    RetryClassifyingErrorHandler(ConsumerRecordRecoverer recoverer, BackOff backOff) {
      super(recoverer, backOff);
    }

    boolean isRetryable(Exception failure) {
      return getExceptionMatcher().match(ErrorHandlingUtils.unwrapIfNeeded(failure));
    }
  }

  @Bean
  MeterFilter legacyTopicMetricsFilter() {
    return new LegacyTopicMetricsFilter();
  }

  /**
   * Boot's factory, with the interceptor and error handler above, converting each event's JSON to
   * the listener's parameter type, and its consumers' metrics tagged with their group. Replaces
   * Boot's own, which would take a JSON converter from a {@code RecordMessageConverter} bean that
   * the producer would then use too, sending JSON strings where the schema expects objects.
   */
  @Bean
  @ConditionalOnMissingBean(name = "kafkaListenerContainerFactory")
  ConcurrentKafkaListenerContainerFactory<Object, Object> kafkaListenerContainerFactory(
      ConcurrentKafkaListenerContainerFactoryConfigurer configurer,
      ConsumerFactory<Object, Object> consumerFactory,
      JsonMapper json,
      ObjectProvider<MeterRegistry> registry) {
    if (consumerFactory instanceof DefaultKafkaConsumerFactory<Object, Object> defaultFactory) {
      registry.ifAvailable(
          meters -> GroupTaggedConsumerMetrics.replaceBootListenerOn(defaultFactory, meters));
    }
    var factory = new ConcurrentKafkaListenerContainerFactory<Object, Object>();
    configurer.configure(factory, consumerFactory);
    factory.setRecordMessageConverter(new StringJacksonJsonMessageConverter(json));
    return factory;
  }
}
