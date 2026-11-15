package com.ecomm.notifications;

import static com.ecomm.notifications.Events.order;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.ecomm.commons.events.EventBackbone;
import com.ecomm.commons.security.FakeKeycloak;
import com.ecomm.notifications.application.port.out.NotificationProvider;
import com.ecomm.notifications.domain.NotificationChannel;
import com.ecomm.notifications.domain.Recipient;
import com.ecomm.notifications.domain.RenderedMessage;
import com.ecomm.notifications.domain.SendOutcome;
import jakarta.mail.Message;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.internet.InternetAddress;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Email over SMTP, to a GreenMail container: a paid Order's confirmation arrives with its subject,
 * its text and HTML bodies and the Customer as its recipient; an address no server could take fails
 * the Notification at once; a message with an unsubscribe link carries RFC 8058's one-click
 * headers; and a server that stops answering is a transient failure, tried six times, then {@code
 * FAILED}. GreenMail takes every recipient, so it can't refuse one with an SMTP reply code. SMTP
 * calls time out after a second here, so a stalled server fails quickly.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "ecomm.notifications.dispatch.poll-every=50ms",
      "ecomm.notifications.dispatch.first-retry-after=20ms",
      "spring.mail.properties.mail.smtp.connectiontimeout=1000",
      "spring.mail.properties.mail.smtp.timeout=1000",
      "spring.mail.properties.mail.smtp.writetimeout=1000",
      "ecomm.notifications.unsubscribe.key=" + NotificationsApiTest.UNSUBSCRIBE_KEY
    })
@AutoConfigureRestTestClient
class EmailDeliveryTest {

  @DynamicPropertySource
  static void infrastructure(DynamicPropertyRegistry registry) {
    FakeKeycloak.registerWith(registry);
    EventBackbone.registerWith(registry);
    Keycloak.registerWith(registry);
    registry.add("spring.datasource.url", NotificationsApiTest.POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", NotificationsApiTest.POSTGRES::getUsername);
    registry.add("spring.datasource.password", NotificationsApiTest.POSTGRES::getPassword);
    registry.add("spring.mail.host", GreenMail.CONTAINER::getHost);
    registry.add("spring.mail.port", GreenMail::smtpPort);
  }

  @Autowired RestTestClient http;
  @Autowired List<NotificationProvider> providers;
  NotificationsClient notifications;

  @BeforeEach
  void setUp() {
    Keycloak.reset();
    notifications = new NotificationsClient(http);
  }

  @Test
  void aConfirmationArrivesWithItsSubjectBothBodiesAndRecipient() throws Exception {
    var customer = UUID.randomUUID().toString();
    var address = "ada-" + customer + "@example.com";
    Keycloak.customer(customer, address, "Ada", "Lovelace");

    var order = order(customer).line("VARIANT-1", 2, 2500).placed().paid();

    var message =
        await()
            .atMost(Duration.ofSeconds(20))
            .until(() -> GreenMail.inbox(address), l -> !l.isEmpty())
            .getFirst();
    assertThat(message.getSubject()).isEqualTo("Your Order " + order.reference() + " is confirmed");
    assertThat(message.getRecipients(Message.RecipientType.TO))
        .containsExactly(new InternetAddress(address, "Ada Lovelace"));
    assertThat(message.getFrom())
        .containsExactly(new InternetAddress("orders@ecomm.local", "Ecomm"));
    assertThat(part(message, "text/plain")).contains("Hello Ada,").contains("Total: €54.99");
    assertThat(part(message, "text/html")).contains("<strong>€54.99</strong>");
    assertThat(message.getHeader("List-Unsubscribe")).isNull();
    var notification = notifications.awaitSettled(NotificationsApiTest.STAFF, order.orderId());
    assertThat(notification.status()).isEqualTo("SENT");
  }

  @Test
  void aMessageWithAnUnsubscribeLinkOffersRfc8058sOneClickUnsubscribe() throws Exception {
    var address = "ada-" + UUID.randomUUID() + "@example.com";
    var link = "http://localhost:8080/api/notifications/unsubscribe/cart-reminders?token=abc.def";
    var email =
        providers.stream().filter(p -> p.channel() == NotificationChannel.EMAIL).findFirst().get();

    var outcome =
        email.send(
            new Recipient("customer-1", address, "Ada", "Lovelace", Locale.ENGLISH),
            new RenderedMessage("You left something in your Cart", "<p>Hi</p>", "Hi", link));

    assertThat(outcome).isInstanceOf(SendOutcome.Sent.class);
    var message =
        await()
            .atMost(Duration.ofSeconds(20))
            .until(() -> GreenMail.inbox(address), l -> !l.isEmpty())
            .getFirst();
    assertThat(message.getHeader("List-Unsubscribe")).containsExactly("<" + link + ">");
    assertThat(message.getHeader("List-Unsubscribe-Post"))
        .containsExactly("List-Unsubscribe=One-Click");
  }

  @Test
  void anAddressNoServerCouldTakeFailsAtOnce() {
    var customer = UUID.randomUUID().toString();
    Keycloak.customer(customer, "not an address", "Ada", "Lovelace");

    var order = order(customer).line("VARIANT-1", 1, 2500).placed().paid();

    var notification = notifications.awaitSettled(NotificationsApiTest.STAFF, order.orderId());
    assertThat(notification.status()).isEqualTo("FAILED");
    assertThat(notification.attempts()).hasSize(1);
    assertThat(notification.attempts().getFirst().outcome()).isEqualTo("PERMANENT_FAILURE");
    assertThat(notification.attempts().getFirst().reason()).isEqualTo("invalidAddress");
  }

  @Test
  void aServerThatStopsAnsweringIsRetriedSixTimesThenFailed() {
    var customer = UUID.randomUUID().toString();
    Keycloak.customer(customer, "ada-" + customer + "@example.com", "Ada", "Lovelace");
    var docker = GreenMail.CONTAINER.getDockerClient();
    var id = GreenMail.CONTAINER.getContainerId();
    docker.pauseContainerCmd(id).exec();
    try {
      var order = order(customer).line("VARIANT-1", 1, 2500).placed().paid();

      var notification =
          await()
              .atMost(Duration.ofSeconds(60))
              .until(
                  () ->
                      notifications
                          .staffList(NotificationsApiTest.STAFF, "orderId=" + order.orderId())
                          .items(),
                  l -> l.size() == 1 && !l.getFirst().status().equals("PENDING"))
              .getFirst();
      assertThat(notification.status()).isEqualTo("FAILED");
      assertThat(notification.attempts())
          .hasSize(6)
          .allSatisfy(a -> assertThat(a.outcome()).isEqualTo("TRANSIENT_FAILURE"))
          .allSatisfy(a -> assertThat(a.reason()).startsWith("smtpUnavailable"));
    } finally {
      docker.unpauseContainerCmd(id).exec();
    }
  }

  /** The text of the message's first part of {@code type}, however deep. */
  private static String part(Part part, String type) throws Exception {
    if (part.isMimeType(type)) {
      return (String) part.getContent();
    }
    if (part.getContent() instanceof Multipart multipart) {
      for (var i = 0; i < multipart.getCount(); i++) {
        var found = part(multipart.getBodyPart(i), type);
        if (found != null) {
          return found;
        }
      }
    }
    return null;
  }
}
