package com.ecomm.supportassistant;

import com.ecomm.supportassistant.application.ConversationService;
import com.ecomm.supportassistant.application.port.in.ConversationsUseCase;
import com.ecomm.supportassistant.application.port.out.ConversationRepository;
import com.ecomm.supportassistant.application.port.out.OrderManagementPort;
import com.ecomm.supportassistant.application.port.out.SupportModel;
import com.ecomm.supportassistant.application.port.out.TimeSource;
import java.time.Duration;
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
  ConversationsUseCase conversationsUseCase(
      ConversationRepository conversations,
      SupportModel model,
      OrderManagementPort orderManagement,
      TimeSource time,
      @Value("${ecomm.support-assistant.conversation-lapses-after}") Duration lapsesAfter) {
    return new ConversationService(conversations, model, orderManagement, time, lapsesAfter);
  }
}
