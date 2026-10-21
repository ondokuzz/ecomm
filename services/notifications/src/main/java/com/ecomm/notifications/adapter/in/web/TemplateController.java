package com.ecomm.notifications.adapter.in.web;

import com.ecomm.notifications.application.port.in.TemplatesUseCase;
import com.ecomm.notifications.application.port.in.TemplatesUseCase.Preview;
import com.ecomm.notifications.domain.MessageType;
import com.ecomm.notifications.domain.NotificationChannel;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The Message templates, for Staff to check their wording: each rendered with its Message type's
 * sample content, for a made-up Customer. Staff can't edit them.
 */
@RestController
@RequestMapping("/templates")
class TemplateController {

  /** A Message type on a channel, as rendered in {@code locale}. */
  record PreviewResponse(
      String messageType, String channel, String locale, String subject, String html, String text) {

    static PreviewResponse of(Preview p) {
      return new PreviewResponse(
          p.type().name(),
          p.channel().name(),
          p.locale().toLanguageTag(),
          p.message().subject(),
          p.message().html(),
          p.message().text());
    }
  }

  /** What to preview in: a locale such as {@code en}, falling back to {@code en}. Optional. */
  record PreviewBody(String locale) {}

  private final TemplatesUseCase templates;

  TemplateController(TemplatesUseCase templates) {
    this.templates = templates;
  }

  /** Every Message type on every channel, in {@code en}. */
  @GetMapping
  @PreAuthorize("hasRole('STAFF')")
  List<PreviewResponse> templates() {
    return templates.previews().stream().map(PreviewResponse::of).toList();
  }

  /** One Message type on one channel; 404 for a type or channel there is none of. */
  @PostMapping("/{type}/{channel}/preview")
  @PreAuthorize("hasRole('STAFF')")
  PreviewResponse preview(
      @PathVariable String type,
      @PathVariable String channel,
      @RequestBody(required = false) PreviewBody body) {
    var messageType = valueOf(MessageType.class, type);
    var notificationChannel = valueOf(NotificationChannel.class, channel);
    if (messageType.isEmpty() || notificationChannel.isEmpty()) {
      throw new NoSuchTemplateException(type, channel);
    }
    var locale =
        body == null || body.locale() == null
            ? Locale.ENGLISH
            : Locale.forLanguageTag(body.locale());
    return PreviewResponse.of(
        templates.preview(messageType.get(), notificationChannel.get(), locale));
  }

  private static <E extends Enum<E>> Optional<E> valueOf(Class<E> type, String name) {
    try {
      return Optional.of(Enum.valueOf(type, name));
    } catch (IllegalArgumentException e) {
      return Optional.empty();
    }
  }

  static class NoSuchTemplateException extends RuntimeException {

    NoSuchTemplateException(String type, String channel) {
      super("There is no Message template for " + type + " on " + channel);
    }
  }

  @ExceptionHandler(NoSuchTemplateException.class)
  ProblemDetail notFound(NoSuchTemplateException e) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
  }
}
