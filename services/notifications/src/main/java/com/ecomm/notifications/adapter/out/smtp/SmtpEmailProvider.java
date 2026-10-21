package com.ecomm.notifications.adapter.out.smtp;

import com.ecomm.notifications.application.port.out.NotificationProvider;
import com.ecomm.notifications.domain.NotificationChannel;
import com.ecomm.notifications.domain.Recipient;
import com.ecomm.notifications.domain.RenderedMessage;
import com.ecomm.notifications.domain.SendOutcome;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import org.eclipse.angus.mail.smtp.SMTPAddressFailedException;
import org.eclipse.angus.mail.smtp.SMTPSendFailedException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/**
 * Email over SMTP: to Mailpit in the compose stack, to any relay elsewhere (ADR 0005). Each message
 * is {@code multipart/alternative}, its text body then its HTML one, from {@code
 * ecomm.notifications.email.from}. Its reference is the message's {@code Message-ID}.
 *
 * <p>An address that isn't one, or a 5xx reply refusing the recipient or the message, is a
 * permanent failure; a 4xx reply, or a server that can't be reached or doesn't answer, is
 * transient. Tests of everything but SMTP turn it off with {@code
 * ecomm.notifications.email.smtp.enabled=false}.
 */
@Component
@ConditionalOnProperty(
    name = "ecomm.notifications.email.smtp.enabled",
    havingValue = "true",
    matchIfMissing = true)
class SmtpEmailProvider implements NotificationProvider {

  private final JavaMailSender mail;
  private final InternetAddress from;

  SmtpEmailProvider(JavaMailSender mail, @Value("${ecomm.notifications.email.from}") String from)
      throws AddressException {
    this.mail = mail;
    this.from = new InternetAddress(from, true);
  }

  @Override
  public NotificationChannel channel() {
    return NotificationChannel.EMAIL;
  }

  @Override
  public SendOutcome send(Recipient recipient, RenderedMessage message) {
    var mime = mail.createMimeMessage();
    try {
      var to = new InternetAddress(recipient.email(), true);
      to.setPersonal(personal(recipient), StandardCharsets.UTF_8.name());
      var helper = new MimeMessageHelper(mime, true, StandardCharsets.UTF_8.name());
      helper.setFrom(from);
      helper.setTo(to);
      helper.setSubject(message.subject());
      helper.setText(message.text(), message.html());
    } catch (AddressException e) {
      return SendOutcome.Failed.permanent("invalidAddress");
    } catch (MessagingException | UnsupportedEncodingException e) {
      return SendOutcome.Failed.permanent("unsendable: " + e.getMessage());
    }
    try {
      mail.send(mime);
      return new SendOutcome.Sent(mime.getMessageID());
    } catch (MailException e) {
      return failureOf(e);
    } catch (MessagingException e) {
      // Only getMessageID, after a send that succeeded, throws it.
      return new SendOutcome.Sent(null);
    }
  }

  private static String personal(Recipient recipient) {
    var name =
        ((recipient.givenName() == null ? "" : recipient.givenName())
                + " "
                + (recipient.familyName() == null ? "" : recipient.familyName()))
            .strip();
    return name.isEmpty() ? null : name;
  }

  /** What a refused send means: the server's reply code, where it gave one, decides. */
  private static SendOutcome.Failed failureOf(MailException e) {
    for (Throwable cause = e; cause != null; cause = next(cause)) {
      if (cause instanceof AddressException) {
        return SendOutcome.Failed.permanent("invalidAddress");
      }
      var code =
          cause instanceof SMTPAddressFailedException a
              ? a.getReturnCode()
              : cause instanceof SMTPSendFailedException s ? s.getReturnCode() : 0;
      if (code >= 500) {
        return SendOutcome.Failed.permanent("smtp " + code + ": " + cause.getMessage().strip());
      }
      if (code >= 400) {
        return SendOutcome.Failed.transientFailure(
            "smtp " + code + ": " + cause.getMessage().strip());
      }
    }
    return SendOutcome.Failed.transientFailure("smtpUnavailable: " + e.getMessage());
  }

  private static Throwable next(Throwable cause) {
    if (cause instanceof MessagingException m && m.getNextException() != null) {
      return m.getNextException();
    }
    if (cause instanceof org.springframework.mail.MailSendException s
        && !s.getFailedMessages().isEmpty()) {
      var failure = s.getFailedMessages().values().iterator().next();
      return failure == cause ? null : failure;
    }
    return cause.getCause() == cause ? null : cause.getCause();
  }
}
