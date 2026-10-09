package com.ecomm.payment.adapter.in.web;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * How Payment tells a Gateway webhook from a forgery: its {@code Gateway-Signature} header is
 * {@code sha256=} and the hex HMAC-SHA256 of the body's bytes, keyed with the secret Payment shares
 * with the gateway.
 */
final class WebhookSignature {

  static final String HEADER = "Gateway-Signature";

  private static final String PREFIX = "sha256=";

  private final byte[] secret;

  WebhookSignature(String secret) {
    if (secret == null || secret.isBlank()) {
      throw new IllegalStateException(
          "payment.webhooks.secret is required: set PAYMENT_WEBHOOK_SECRET");
    }
    this.secret = secret.getBytes(StandardCharsets.UTF_8);
  }

  /** Whether {@code signature} signs {@code body}, compared in constant time. */
  boolean signs(String signature, byte[] body) {
    if (signature == null || !signature.startsWith(PREFIX)) {
      return false;
    }
    var expected = (PREFIX + HexFormat.of().formatHex(hmac(body))).getBytes(StandardCharsets.UTF_8);
    return MessageDigest.isEqual(expected, signature.getBytes(StandardCharsets.UTF_8));
  }

  private byte[] hmac(byte[] body) {
    try {
      var mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret, "HmacSHA256"));
      return mac.doFinal(body);
    } catch (NoSuchAlgorithmException | InvalidKeyException e) {
      throw new IllegalStateException("HMAC-SHA256 is unavailable", e);
    }
  }
}
