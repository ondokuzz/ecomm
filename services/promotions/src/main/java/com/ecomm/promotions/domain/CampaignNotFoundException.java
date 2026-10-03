package com.ecomm.promotions.domain;

/** No Campaign has this ID. */
public class CampaignNotFoundException extends RuntimeException {

  public CampaignNotFoundException(String id) {
    super("No Campaign " + id);
  }
}
