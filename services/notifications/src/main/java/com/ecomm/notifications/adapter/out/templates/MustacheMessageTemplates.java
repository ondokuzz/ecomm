package com.ecomm.notifications.adapter.out.templates;

import com.ecomm.notifications.application.port.out.MessageTemplates;
import com.ecomm.notifications.domain.MessageType;
import com.ecomm.notifications.domain.Messages;
import com.ecomm.notifications.domain.NotificationChannel;
import com.ecomm.notifications.domain.Recipient;
import com.ecomm.notifications.domain.RenderedMessage;
import com.samskivert.mustache.Mustache;
import com.samskivert.mustache.Template;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

/**
 * Message templates as Mustache (JMustache) resources, one file per part: {@code
 * <location>/<message-type>/<channel>/<locale>/<part>.mustache}, such as {@code
 * order-confirmed/email/en/subject.mustache}. The HTML part escapes what it is given; the others
 * don't. A name the model lacks fails the render rather than leaving a gap.
 *
 * <p>Built at start-up, it checks that every Message type has every part of every Notification
 * channel in {@code en}, that every locale it has is complete, and that each renders its Message
 * type's sample content; otherwise it throws, naming every problem, and the service doesn't start.
 */
@Component
class MustacheMessageTemplates implements MessageTemplates {

  static final Locale FALLBACK = Locale.ENGLISH;

  private record Key(MessageType type, NotificationChannel channel, String locale) {}

  private final Map<Key, Map<String, Template>> templates = new HashMap<>();

  MustacheMessageTemplates(@Value("${ecomm.notifications.templates.location}") String location) {
    var html = Mustache.compiler().escapeHTML(true);
    var plain = Mustache.compiler().escapeHTML(false);
    var problems = new ArrayList<String>();
    var base = location.endsWith("/") ? location : location + "/";
    for (var resource : resources(base + "**/*.mustache")) {
      var path = relativePath(resource, base);
      var segments = path.split("/");
      var key = segments.length == 4 ? key(segments) : null;
      if (key == null) {
        problems.add(path + " isn't <message-type>/<channel>/<locale>/<part>.mustache");
        continue;
      }
      var part = segments[3].substring(0, segments[3].length() - ".mustache".length());
      if (!key.channel().parts().contains(part)) {
        problems.add(path + ": " + key.channel() + " has no part " + part);
        continue;
      }
      try (var reader = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8)) {
        var compiler = part.equals("html") ? html : plain;
        templates
            .computeIfAbsent(key, k -> new LinkedHashMap<>())
            .put(part, compiler.compile(reader));
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      } catch (RuntimeException e) {
        problems.add(path + " doesn't compile: " + e.getMessage());
      }
    }
    check(problems);
    if (!problems.isEmpty()) {
      throw new IllegalStateException(
          "The Message templates under "
              + location
              + " are incomplete:\n  "
              + String.join("\n  ", problems));
    }
  }

  private void check(List<String> problems) {
    for (var type : MessageType.values()) {
      for (var channel : NotificationChannel.values()) {
        if (!templates.containsKey(new Key(type, channel, FALLBACK.toLanguageTag()))) {
          problems.add(dir(type) + "/" + dir(channel) + "/en/ is missing");
        }
      }
    }
    templates.forEach(
        (key, parts) -> {
          var where = dir(key.type()) + "/" + dir(key.channel()) + "/" + key.locale() + "/";
          for (var part : key.channel().parts()) {
            if (!parts.containsKey(part)) {
              problems.add(where + part + ".mustache is missing");
            }
          }
          var locale = Locale.forLanguageTag(key.locale());
          var model = Messages.model(key.type().sample(), Recipient.SAMPLE, locale);
          parts.forEach(
              (part, template) -> {
                try {
                  template.execute(model);
                } catch (RuntimeException e) {
                  problems.add(
                      where + part + ".mustache doesn't render its sample: " + e.getMessage());
                }
              });
        });
  }

  @Override
  public Locale localeFor(Locale wanted) {
    if (wanted != null) {
      for (var tag : List.of(wanted.toLanguageTag(), wanted.getLanguage())) {
        if (templates.keySet().stream().anyMatch(k -> k.locale().equals(tag))) {
          return Locale.forLanguageTag(tag);
        }
      }
    }
    return FALLBACK;
  }

  @Override
  public RenderedMessage render(
      MessageType type, NotificationChannel channel, Locale locale, Map<String, Object> model) {
    var parts = templates.get(new Key(type, channel, locale.toLanguageTag()));
    if (parts == null) {
      parts = templates.get(new Key(type, channel, FALLBACK.toLanguageTag()));
    }
    var rendered = new LinkedHashMap<String, String>();
    parts.forEach((part, template) -> rendered.put(part, template.execute(model).strip()));
    return RenderedMessage.of(rendered);
  }

  private static Key key(String[] segments) {
    var type = valueOf(MessageType.class, segments[0]);
    var channel = valueOf(NotificationChannel.class, segments[1]);
    return type == null || channel == null ? null : new Key(type, channel, segments[2]);
  }

  /** The enum constant whose name, lower-cased with dashes, is {@code dir}. */
  private static <E extends Enum<E>> E valueOf(Class<E> type, String dir) {
    for (var constant : type.getEnumConstants()) {
      if (dir(constant).equals(dir)) {
        return constant;
      }
    }
    return null;
  }

  static String dir(Enum<?> constant) {
    return constant.name().toLowerCase(Locale.ROOT).replace('_', '-');
  }

  private static Resource[] resources(String pattern) {
    try {
      return new PathMatchingResourcePatternResolver().getResources(pattern);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static String relativePath(Resource resource, String base) {
    try {
      var url = resource.getURL().toString();
      var root = base.substring(base.indexOf(':') + 1).replaceFirst("^/+", "");
      var at = url.lastIndexOf(root);
      return at < 0 ? resource.getFilename() : url.substring(at + root.length());
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
