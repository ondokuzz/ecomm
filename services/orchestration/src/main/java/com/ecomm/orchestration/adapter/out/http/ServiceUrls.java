package com.ecomm.orchestration.adapter.out.http;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Where the Saga's steps find each context they call. */
@ConfigurationProperties("ecomm.orchestration")
record ServiceUrls(
    String orderManagementUrl,
    String paymentUrl,
    String inventoryUrl,
    String cartUrl,
    String checkoutPricingUrl) {}
