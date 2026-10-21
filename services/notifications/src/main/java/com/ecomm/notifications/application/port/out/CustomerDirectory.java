package com.ecomm.notifications.application.port.out;

import com.ecomm.notifications.domain.Recipient;
import java.util.Optional;

/**
 * Who a Customer is, as Identity & Access holds them: their email address and names. Notifications
 * keeps no copy of them.
 */
public interface CustomerDirectory {

  /**
   * The Customer as a Recipient; empty for a Customer the directory doesn't know, or one with no
   * email address.
   *
   * @throws DirectoryUnavailableException when the directory can't answer now
   */
  Optional<Recipient> find(String customerId);

  /** The directory didn't answer; asking again later may work. */
  class DirectoryUnavailableException extends RuntimeException {

    public DirectoryUnavailableException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
