package com.ecomm.orchestration.adapter.out.http;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ServiceUrls.class)
class ServiceUrlsConfiguration {}
