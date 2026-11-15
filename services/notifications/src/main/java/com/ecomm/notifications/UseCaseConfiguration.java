package com.ecomm.notifications;

import com.ecomm.notifications.application.CartEventsService;
import com.ecomm.notifications.application.DispatchService;
import com.ecomm.notifications.application.MockProviderReadsService;
import com.ecomm.notifications.application.NotificationReadsService;
import com.ecomm.notifications.application.OrderEventsService;
import com.ecomm.notifications.application.PaymentEventsService;
import com.ecomm.notifications.application.PreferencesService;
import com.ecomm.notifications.application.ProductNamesService;
import com.ecomm.notifications.application.RmaEventsService;
import com.ecomm.notifications.application.ShipmentEventsService;
import com.ecomm.notifications.application.TemplatesService;
import com.ecomm.notifications.application.UnsubscribeService;
import com.ecomm.notifications.application.port.in.CartEventsUseCase;
import com.ecomm.notifications.application.port.in.DispatchUseCase;
import com.ecomm.notifications.application.port.in.MockProviderReadsUseCase;
import com.ecomm.notifications.application.port.in.NotificationReadsUseCase;
import com.ecomm.notifications.application.port.in.OrderEventsUseCase;
import com.ecomm.notifications.application.port.in.PaymentEventsUseCase;
import com.ecomm.notifications.application.port.in.PreferencesUseCase;
import com.ecomm.notifications.application.port.in.ProductNamesUseCase;
import com.ecomm.notifications.application.port.in.RmaEventsUseCase;
import com.ecomm.notifications.application.port.in.ShipmentEventsUseCase;
import com.ecomm.notifications.application.port.in.TemplatesUseCase;
import com.ecomm.notifications.application.port.in.UnsubscribeUseCase;
import com.ecomm.notifications.application.port.out.CartReminderCounter;
import com.ecomm.notifications.application.port.out.Carts;
import com.ecomm.notifications.application.port.out.CustomerDirectory;
import com.ecomm.notifications.application.port.out.ListedProducts;
import com.ecomm.notifications.application.port.out.MessageTemplates;
import com.ecomm.notifications.application.port.out.MockProviderLog;
import com.ecomm.notifications.application.port.out.NotificationPreferencesStore;
import com.ecomm.notifications.application.port.out.NotificationProvider;
import com.ecomm.notifications.application.port.out.NotificationStore;
import com.ecomm.notifications.application.port.out.ProductNames;
import com.ecomm.notifications.application.port.out.ReturnAddresses;
import com.ecomm.notifications.application.port.out.TimeSource;
import com.ecomm.notifications.application.port.out.Transactions;
import com.ecomm.notifications.domain.RetryPolicy;
import com.ecomm.notifications.domain.UnsubscribeTokens;
import java.time.Duration;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires use cases as beans, so the application layer stays free of Spring annotations. Adapters are
 * ordinary Spring components.
 */
@Configuration
class UseCaseConfiguration {

  @Bean
  OrderEventsUseCase orderEventsUseCase(
      NotificationStore notifications,
      NotificationPreferencesStore preferences,
      ProductNames products,
      Transactions transactions,
      TimeSource time) {
    return new OrderEventsService(notifications, preferences, products, transactions, time);
  }

  @Bean
  ShipmentEventsUseCase shipmentEventsUseCase(
      NotificationStore notifications,
      NotificationPreferencesStore preferences,
      Transactions transactions,
      TimeSource time) {
    return new ShipmentEventsService(notifications, preferences, transactions, time);
  }

  @Bean
  PaymentEventsUseCase paymentEventsUseCase(
      NotificationStore notifications,
      NotificationPreferencesStore preferences,
      Transactions transactions,
      TimeSource time) {
    return new PaymentEventsService(notifications, preferences, transactions, time);
  }

  @Bean
  RmaEventsUseCase rmaEventsUseCase(
      NotificationStore notifications,
      NotificationPreferencesStore preferences,
      ReturnAddresses returnAddresses,
      Transactions transactions,
      TimeSource time) {
    return new RmaEventsService(notifications, preferences, returnAddresses, transactions, time);
  }

  /**
   * Cart reminders, linking to the Cart on the Storefront at {@code
   * ecomm.notifications.storefront-url}, and to {@code ecomm.notifications.unsubscribe.url} with a
   * token signed by {@code ecomm.notifications.unsubscribe.key}.
   */
  @Bean
  CartEventsUseCase cartEventsUseCase(
      NotificationStore notifications,
      NotificationPreferencesStore preferences,
      Carts carts,
      ProductNames products,
      ListedProducts listings,
      @Value("${ecomm.notifications.storefront-url}") String storefrontUrl,
      UnsubscribeTokens tokens,
      @Value("${ecomm.notifications.unsubscribe.url}") String unsubscribeUrl,
      Transactions transactions,
      TimeSource time,
      CartReminderCounter counter) {
    return new CartEventsService(
        notifications,
        preferences,
        carts,
        products,
        listings,
        storefrontUrl + "/cart",
        tokens,
        unsubscribeUrl,
        transactions,
        time,
        counter);
  }

  /** Signed with the secret {@code notifications-unsubscribe-key} (ADR 0011). */
  @Bean
  UnsubscribeTokens unsubscribeTokens(@Value("${ecomm.notifications.unsubscribe.key}") String key) {
    return new UnsubscribeTokens(key);
  }

  @Bean
  UnsubscribeUseCase unsubscribeUseCase(
      UnsubscribeTokens tokens, NotificationPreferencesStore preferences, TimeSource time) {
    return new UnsubscribeService(tokens, preferences, time);
  }

  @Bean
  PreferencesUseCase preferencesUseCase(NotificationPreferencesStore preferences, TimeSource time) {
    return new PreferencesService(preferences, time);
  }

  @Bean
  MockProviderReadsUseCase mockProviderReadsUseCase(MockProviderLog log) {
    return new MockProviderReadsService(log);
  }

  @Bean
  ProductNamesUseCase productNamesUseCase(ProductNames products, Transactions transactions) {
    return new ProductNamesService(products, transactions);
  }

  @Bean
  NotificationReadsUseCase notificationReadsUseCase(NotificationStore notifications) {
    return new NotificationReadsService(notifications);
  }

  @Bean
  TemplatesUseCase templatesUseCase(MessageTemplates templates) {
    return new TemplatesService(templates);
  }

  @Bean
  DispatchUseCase dispatchUseCase(
      NotificationStore notifications,
      NotificationPreferencesStore preferences,
      CustomerDirectory directory,
      MessageTemplates templates,
      List<NotificationProvider> providers,
      @Value("${ecomm.notifications.dispatch.attempts}") int attempts,
      @Value("${ecomm.notifications.dispatch.first-retry-after}") Duration firstRetryAfter,
      Transactions transactions,
      TimeSource time) {
    return new DispatchService(
        notifications,
        preferences,
        directory,
        templates,
        providers,
        new RetryPolicy(attempts, firstRetryAfter),
        transactions,
        time);
  }
}
