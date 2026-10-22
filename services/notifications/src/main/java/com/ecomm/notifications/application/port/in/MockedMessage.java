package com.ecomm.notifications.application.port.in;

import com.ecomm.notifications.domain.NotificationChannel;
import java.time.Instant;

/**
 * One message a mock SMS or push provider took: its reference, the Customer, where it went (a
 * mobile number, or for push the Customer), its title (push only, otherwise {@code null}) and text.
 */
public record MockedMessage(
    String reference,
    NotificationChannel channel,
    String customerId,
    String to,
    String title,
    String text,
    Instant sentAt) {}
