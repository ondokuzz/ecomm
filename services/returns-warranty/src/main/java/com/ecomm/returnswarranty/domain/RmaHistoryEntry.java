package com.ecomm.returnswarranty.domain;

import java.time.Instant;

/**
 * One entry of an RMA's history: the status it moved to, who moved it and when, and a note. Who is
 * a Staff member's ID, {@link #CUSTOMER} for the request, or {@link #ORCHESTRATION} for the returns
 * Saga's steps. An RMA decision, Staff approving or rejecting it, is one kind of entry. An RMA's
 * history only ever grows.
 *
 * @param note null when the step had none; a rejection's reason, an Inspection's note, a hold's
 *     reason
 */
public record RmaHistoryEntry(RmaStatus status, String by, Instant at, String note) {

  public static final String CUSTOMER = "CUSTOMER";
  public static final String ORCHESTRATION = "ORCHESTRATION";
}
