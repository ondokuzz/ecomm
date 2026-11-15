package com.ecomm.notifications.domain;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * The token a Cart reminder's unsubscribe link carries, so a Customer turns cart reminders off
 * without signing in: it names only their Customer ID and the preference, expires 30 days after it
 * was issued, and is signed with HMAC-SHA256 under {@code notifications-unsubscribe-key}. It reads
 * {@code <payload>.<signature>}, each base64url, the payload {@code
 * <customerId>|cartReminders|<expires, epoch seconds>}, so it is safe in a URL as it is. Using it
 * again is harmless, so it isn't spent: it simply turns off what is already off.
 */
public final class UnsubscribeTokens {

  public static final Duration VALID_FOR = Duration.ofDays(30);

  private static final String PREFERENCE = "cartReminders";
  private static final String ALGORITHM = "HmacSHA256";
  private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
  private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

  private final SecretKeySpec key;

  public UnsubscribeTokens(String key) {
    if (key == null || key.isBlank()) {
      throw new IllegalArgumentException("The unsubscribe key is empty");
    }
    this.key = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), ALGORITHM);
  }

  /**
   * A token turning off {@code customerId}'s cart reminders, valid for 30 days from {@code now}.
   */
  public String issue(String customerId, Instant now) {
    var expires = now.plus(VALID_FOR).getEpochSecond();
    var payload =
        ENCODER.encodeToString(
            (customerId + "|" + PREFERENCE + "|" + expires).getBytes(StandardCharsets.UTF_8));
    return payload + "." + ENCODER.encodeToString(sign(payload));
  }

  /**
   * The Customer whose cart reminders {@code token} turns off, or empty for a token this key didn't
   * sign, one changed since, one for another preference, or one expired at {@code now}.
   */
  public Optional<String> customerOf(String token, Instant now) {
    if (token == null) {
      return Optional.empty();
    }
    var dot = token.indexOf('.');
    if (dot < 0) {
      return Optional.empty();
    }
    var payload = token.substring(0, dot);
    try {
      var signature = DECODER.decode(token.substring(dot + 1));
      if (!MessageDigest.isEqual(signature, sign(payload))) {
        return Optional.empty();
      }
      var fields = new String(DECODER.decode(payload), StandardCharsets.UTF_8).split("\\|", -1);
      if (fields.length != 3 || fields[0].isEmpty() || !PREFERENCE.equals(fields[1])) {
        return Optional.empty();
      }
      if (!now.isBefore(Instant.ofEpochSecond(Long.parseLong(fields[2])))) {
        return Optional.empty();
      }
      return Optional.of(fields[0]);
    } catch (IllegalArgumentException e) {
      return Optional.empty(); // not base64url, or no number where the expiry goes
    }
  }

  private byte[] sign(String payload) {
    try {
      var mac = Mac.getInstance(ALGORITHM);
      mac.init(key);
      return mac.doFinal(payload.getBytes(StandardCharsets.US_ASCII));
    } catch (NoSuchAlgorithmException | InvalidKeyException e) {
      throw new IllegalStateException(ALGORITHM + " is part of every JDK", e);
    }
  }
}
