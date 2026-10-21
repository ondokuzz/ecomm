package com.ecomm.notifications;

import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.function.Predicate;
import org.springframework.test.web.servlet.client.RestTestClient;

/** Test-only: reads the Notification ledger over HTTP, as Staff and Customers do. */
final class NotificationsClient {

  record Page(List<NotificationView> items, int page, int size, long total) {}

  record NotificationView(
      String id,
      String messageType,
      String channel,
      String customerId,
      String orderId,
      String rmaId,
      String recipient,
      String status,
      Message message,
      List<AttemptView> attempts,
      Instant createdAt,
      Instant sentAt,
      Instant nextAttemptAt) {}

  record Message(String subject, String html, String text) {}

  record AttemptView(int number, Instant at, String outcome, String reason) {}

  private final RestTestClient http;

  NotificationsClient(RestTestClient http) {
    this.http = http;
  }

  /** Staff's list for {@code query}, such as {@code orderId=...}. */
  Page staffList(String token, String query) {
    return http.get()
        .uri("/notifications?" + query)
        .header("Authorization", "Bearer " + token)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(Page.class)
        .returnResult()
        .getResponseBody();
  }

  Page mine(String token) {
    return http.get()
        .uri("/notifications/me")
        .header("Authorization", "Bearer " + token)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(Page.class)
        .returnResult()
        .getResponseBody();
  }

  /** The Order's Notifications, as Staff read them, once {@code until} holds. */
  List<NotificationView> awaitForOrder(
      String staff, String orderId, Predicate<List<NotificationView>> until) {
    return await()
        .atMost(Duration.ofSeconds(20))
        .until(() -> staffList(staff, "orderId=" + orderId).items(), until);
  }

  /** The Order's one Notification, once it is no longer {@code PENDING}. */
  NotificationView awaitSettled(String staff, String orderId) {
    return awaitForOrder(
            staff, orderId, l -> l.size() == 1 && !l.getFirst().status().equals("PENDING"))
        .getFirst();
  }
}
