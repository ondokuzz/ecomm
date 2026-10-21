package com.ecomm.notifications;

import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import java.util.List;
import java.util.Properties;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

/**
 * Test-only: a GreenMail mail server in Testcontainers, taking any message over SMTP and serving
 * each recipient's mailbox over IMAP, with no passwords.
 */
final class GreenMail {

  static final int SMTP = 3025;
  static final int IMAP = 3143;

  @SuppressWarnings("resource")
  static final GenericContainer<?> CONTAINER =
      new GenericContainer<>("greenmail/standalone:2.1.13")
          .withEnv(
              "GREENMAIL_OPTS",
              "-Dgreenmail.setup.test.smtp -Dgreenmail.setup.test.imap -Dgreenmail.hostname=0.0.0.0"
                  + " -Dgreenmail.auth.disabled")
          .withExposedPorts(SMTP, IMAP)
          .waitingFor(Wait.forListeningPorts(SMTP, IMAP));

  static {
    CONTAINER.start();
  }

  private GreenMail() {}

  static int smtpPort() {
    return CONTAINER.getMappedPort(SMTP);
  }

  /** The messages in {@code address}'s inbox, read whole. */
  static List<Message> inbox(String address) {
    var properties = new Properties();
    properties.put("mail.imap.host", CONTAINER.getHost());
    properties.put("mail.imap.port", String.valueOf(CONTAINER.getMappedPort(IMAP)));
    try (var store = Session.getInstance(properties).getStore("imap")) {
      store.connect(address, address);
      try (var inbox = store.getFolder("INBOX")) {
        inbox.open(Folder.READ_ONLY);
        var messages = List.of(inbox.getMessages());
        for (var message : messages) {
          message.getContent(); // Reads it before the folder closes.
        }
        return messages.stream().map(GreenMail::copy).toList();
      }
    } catch (MessagingException | java.io.IOException e) {
      throw new IllegalStateException(e);
    }
  }

  private static Message copy(Message message) {
    try {
      return new jakarta.mail.internet.MimeMessage((jakarta.mail.internet.MimeMessage) message);
    } catch (MessagingException e) {
      throw new IllegalStateException(e);
    }
  }
}
