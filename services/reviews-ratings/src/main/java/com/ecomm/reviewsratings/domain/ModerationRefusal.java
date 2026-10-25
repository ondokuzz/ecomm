package com.ecomm.reviewsratings.domain;

/** Why a moderation action doesn't apply to a review as it is now, by the code the API answers. */
public enum ModerationRefusal {
  NOT_HELD("notHeld", "Only a held review can be published."),
  NO_OPEN_REPORTS("noOpenReports", "Only a published review with open reports has any to dismiss."),
  ALREADY_REMOVED("alreadyRemoved", "The review is removed already.");

  private final String code;
  private final String message;

  ModerationRefusal(String code, String message) {
    this.code = code;
    this.message = message;
  }

  public String code() {
    return code;
  }

  public String message() {
    return message;
  }
}
