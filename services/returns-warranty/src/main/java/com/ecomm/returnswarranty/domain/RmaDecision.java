package com.ecomm.returnswarranty.domain;

import java.time.Instant;

/**
 * An RMA decision: Staff approving or rejecting an RMA, who did and when, with the note they gave,
 * which a rejection always has. An RMA's decision history only ever grows.
 *
 * @param note null when an approval has none
 */
public record RmaDecision(RmaStatus status, String decidedBy, Instant decidedAt, String note) {}
