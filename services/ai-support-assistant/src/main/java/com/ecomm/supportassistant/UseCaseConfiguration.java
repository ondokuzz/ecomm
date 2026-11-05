package com.ecomm.supportassistant;

import com.ecomm.supportassistant.application.ConversationService;
import com.ecomm.supportassistant.application.port.in.ConversationsUseCase;
import com.ecomm.supportassistant.application.port.out.ConversationRepository;
import com.ecomm.supportassistant.application.port.out.FulfillmentShippingPort;
import com.ecomm.supportassistant.application.port.out.OrderManagementPort;
import com.ecomm.supportassistant.application.port.out.PaymentPort;
import com.ecomm.supportassistant.application.port.out.ReturnsWarrantyPort;
import com.ecomm.supportassistant.application.port.out.SearchDiscoveryPort;
import com.ecomm.supportassistant.application.port.out.SupportModel;
import com.ecomm.supportassistant.application.port.out.TimeSource;
import java.time.Duration;
import java.util.concurrent.Executor;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Wires use cases as beans, so the application layer stays free of Spring annotations. Adapters are
 * ordinary Spring components.
 */
@Configuration
class UseCaseConfiguration {

  @Bean
  ConversationsUseCase conversationsUseCase(
      ConversationRepository conversations,
      SupportModel model,
      OrderManagementPort orderManagement,
      FulfillmentShippingPort fulfillmentShipping,
      PaymentPort payment,
      ReturnsWarrantyPort returnsWarranty,
      SearchDiscoveryPort searchDiscovery,
      TimeSource time,
      @Value("${ecomm.support-assistant.conversation-lapses-after}") Duration lapsesAfter) {
    return new ConversationService(
        conversations,
        model,
        orderManagement,
        fulfillmentShipping,
        payment,
        returnsWarranty,
        searchDiscovery,
        lookups(),
        time,
        lapsesAfter);
  }

  /**
   * Runs each lookup on a virtual thread of its own, with the security context and the MDC of the
   * thread that hands it over: so it reads with the Customer's token (ADR 0002) and sends and logs
   * the request's Correlation ID.
   */
  private static Executor lookups() {
    return lookup -> {
      var security = SecurityContextHolder.getContext();
      var mdc = MDC.getCopyOfContextMap();
      Thread.ofVirtual()
          .name("lookup")
          .start(
              () -> {
                SecurityContextHolder.setContext(security);
                if (mdc != null) {
                  MDC.setContextMap(mdc);
                }
                try {
                  lookup.run();
                } finally {
                  SecurityContextHolder.clearContext();
                  MDC.clear();
                }
              });
    };
  }
}
