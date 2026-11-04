package com.ecomm.supportassistant.application.port.out;

import com.ecomm.supportassistant.domain.Conversation;
import java.time.Duration;
import java.util.Optional;

/** Where Conversations are kept until they lapse. */
public interface ConversationRepository {

  /** Saves it, in place of what was saved before, to be dropped {@code timeToLive} from now. */
  void save(Conversation conversation, Duration timeToLive);

  Optional<Conversation> find(String conversationId);

  void delete(String conversationId);
}
