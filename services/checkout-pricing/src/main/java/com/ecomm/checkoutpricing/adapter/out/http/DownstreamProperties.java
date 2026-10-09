package com.ecomm.checkoutpricing.adapter.out.http;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Where Checkout finds each service it calls. */
@ConfigurationProperties("ecomm.checkout")
record DownstreamProperties(
    String cartUrl, String catalogUrl, String inventoryUrl, String promotionsUrl) {}
