package com.ecomm.notifications.application;

import com.ecomm.notifications.application.port.in.MockProviderReadsUseCase;
import com.ecomm.notifications.application.port.in.MockedMessage;
import com.ecomm.notifications.application.port.in.Page;
import com.ecomm.notifications.application.port.out.MockProviderLog;
import com.ecomm.notifications.domain.NotificationChannel;

public class MockProviderReadsService implements MockProviderReadsUseCase {

  private final MockProviderLog log;

  public MockProviderReadsService(MockProviderLog log) {
    this.log = log;
  }

  @Override
  public Page<MockedMessage> sent(
      NotificationChannel channel, String customerId, int page, int size) {
    if (channel == NotificationChannel.EMAIL) {
      throw new IllegalArgumentException("Email goes over SMTP, not through a mock provider");
    }
    return log.find(channel, customerId, page, size);
  }
}
