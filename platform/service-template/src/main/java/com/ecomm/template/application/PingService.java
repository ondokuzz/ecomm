package com.ecomm.template.application;

import com.ecomm.template.application.port.in.PingUseCase;
import com.ecomm.template.application.port.out.TimeSource;
import com.ecomm.template.domain.Pong;

public class PingService implements PingUseCase {

  private final String serviceName;
  private final TimeSource timeSource;

  public PingService(String serviceName, TimeSource timeSource) {
    this.serviceName = serviceName;
    this.timeSource = timeSource;
  }

  @Override
  public Pong ping(String customerId) {
    return new Pong(serviceName, customerId, timeSource.now());
  }
}
