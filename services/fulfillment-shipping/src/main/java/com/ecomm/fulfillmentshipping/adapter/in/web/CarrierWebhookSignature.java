package com.ecomm.fulfillmentshipping.adapter.in.web;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * How Fulfillment & Shipping tells a Carrier webhook from a forgery: its {@code Carrier-Signature}
 * header is {@code sha256=} and the hex HMAC-SHA256 of the body's bytes, keyed with the secret it
 * shares with the Carrier.
 */
final class CarrierWebhookSignature {

  static final String HEADER = "Carrier-Signature";

  private static final String PREFIX = "sha256=";

  private final byte[] secret;

  CarrierWebhookSignature(String secret) {
    if (secret == null || secret.isBlank()) {
      throw new IllegalStateException(
          "ecomm.fulfillment-shipping.carrier-webhooks.secret is required: compose gives it as a"
              + " secret");
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
