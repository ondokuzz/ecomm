package com.ecomm.checkoutpricing.adapter.in.redis;

import com.ecomm.checkoutpricing.application.port.in.CheckoutUseCase;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * Tells Checkout when Redis drops a Checkout Session, {@code checkout-session:<ID>}, once its time
 * to live runs out. Redis says so only with keyspace notifications for expired keys on ({@code
 * notify-keyspace-events Ex}), which Compose's Redis has; Checkout doesn't turn them on itself, so
 * as not to change a setting that other services' keys share. Ending or replacing a session deletes
 * its key, which Redis doesn't report as expired. Every replica hears each drop.
 */
@Configuration
class SessionExpiryListener implements MessageListener {

  private static final Logger log = LoggerFactory.getLogger(SessionExpiryListener.class);

  // RedisCheckoutSessionRepository's key for a session.
  private static final String SESSION_KEY = "checkout-session:";

  private final CheckoutUseCase checkout;

  SessionExpiryListener(CheckoutUseCase checkout) {
    this.checkout = checkout;
  }

  @Bean
  RedisMessageListenerContainer sessionExpiries(RedisConnectionFactory redis) {
    var container = new RedisMessageListenerContainer();
    container.setConnectionFactory(redis);
    container.addMessageListener(this, new PatternTopic("__keyevent@*__:expired"));
    return container;
  }

  @Override
  public void onMessage(Message message, byte[] pattern) {
    var key = new String(message.getBody(), StandardCharsets.UTF_8);
    if (!key.startsWith(SESSION_KEY)) {
      return;
    }
    var sessionId = key.substring(SESSION_KEY.length());
    try {
      checkout.lapsed(sessionId);
    } catch (RuntimeException e) {
      log.warn("Couldn't count lapsed Checkout Session {} in the funnel", sessionId, e);
    }
  }
}
