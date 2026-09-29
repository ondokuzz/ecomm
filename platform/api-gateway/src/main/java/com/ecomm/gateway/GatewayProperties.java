package com.ecomm.gateway;

import java.net.URI;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * The services the gateway routes to, under {@code ecomm.gateway.services.<name>}. Each is reached
 * at {@code /api/<name>/**}, with the prefix stripped. Paths below are the service's own, without
 * the prefix, as Spring path patterns.
 *
 * @param services by the name that makes up their prefix
 */
@ConfigurationProperties("ecomm.gateway")
record GatewayProperties(@DefaultValue Map<String, Service> services) {

  /**
   * @param uri where the service listens
   * @param publicReads paths anyone may {@code GET} without a token
   * @param internal endpoints only other services call, never routed: a path pattern for every
   *     method, or a method and a path pattern such as {@code POST /orders}
   */
  record Service(
      URI uri, @DefaultValue List<String> publicReads, @DefaultValue List<String> internal) {}
}
