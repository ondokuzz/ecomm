package com.ecomm.template.adapter.in.web;

import com.ecomm.commons.security.CurrentCustomer;
import com.ecomm.template.application.port.in.PingUseCase;
import java.time.Instant;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class PingController {

  private final PingUseCase pingUseCase;

  PingController(PingUseCase pingUseCase) {
    this.pingUseCase = pingUseCase;
  }

  @GetMapping("/ping")
  PingResponse ping(CurrentCustomer customer) {
    var pong = pingUseCase.ping(customer.id());
    return new PingResponse(pong.service(), pong.customerId(), pong.at());
  }

  record PingResponse(String service, String customerId, Instant at) {}
}
