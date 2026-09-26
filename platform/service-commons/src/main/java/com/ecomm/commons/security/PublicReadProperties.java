package com.ecomm.commons.security;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Paths anyone may {@code GET} without a token, such as a public product listing. Set them with
 * {@code ecomm.security.public-read-paths}, as Spring path patterns. Other methods on these paths
 * still need a token.
 */
@ConfigurationProperties("ecomm.security")
public record PublicReadProperties(@DefaultValue List<String> publicReadPaths) {}
