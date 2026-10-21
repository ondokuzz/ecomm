package com.ecomm.notifications.application;

import com.ecomm.notifications.application.port.in.TemplatesUseCase;
import com.ecomm.notifications.application.port.out.MessageTemplates;
import com.ecomm.notifications.domain.MessageType;
import com.ecomm.notifications.domain.Messages;
import com.ecomm.notifications.domain.NotificationChannel;
import com.ecomm.notifications.domain.Recipient;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Renders each template for the sample Recipient, as the start-up check does. */
public class TemplatesService implements TemplatesUseCase {

  private final MessageTemplates templates;

  public TemplatesService(MessageTemplates templates) {
    this.templates = templates;
  }

  @Override
  public List<Preview> previews() {
    var previews = new ArrayList<Preview>();
    for (var type : MessageType.values()) {
      for (var channel : NotificationChannel.values()) {
        previews.add(preview(type, channel, Locale.ENGLISH));
      }
    }
    return previews;
  }

  @Override
  public Preview preview(MessageType type, NotificationChannel channel, Locale locale) {
    var resolved = templates.localeFor(locale);
    var model = Messages.model(type.sample(), Recipient.SAMPLE, resolved);
    return new Preview(type, channel, resolved, templates.render(type, channel, resolved, model));
  }
}
