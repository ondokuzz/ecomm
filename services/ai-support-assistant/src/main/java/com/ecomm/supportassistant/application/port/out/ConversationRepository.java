package com.ecomm.supportassistant.application.port.out;

import com.ecomm.supportassistant.domain.Conversation;
import java.time.Duration;
import java.util.Optional;

/** Where Conversations are kept until they lapse. */
public interface ConversationRepository {

  /** Keeps a new Conversation, to be dropped {@code timeToLive} from now. */
  void create(Conversation conversation, Duration timeToLive);

  /**
   * Saves it in place of what was saved before, to be dropped {@code timeToLive} from now, only if
   * it is still kept: one ended or lapsed meanwhile stays gone.
   *
   * @return whether it was still kept, and so replaced
   */
  boolean replace(Conversation conversation, Duration timeToLive);

  Optional<Conversation> find(String conversationId);

  void delete(String conversationId);
}
