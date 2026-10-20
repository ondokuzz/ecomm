package com.ecomm.returnswarranty.domain;

/**
 * Staff's inspection of an RMA's received units: its outcome, and a note, which a refusing outcome
 * always has.
 *
 * @param note null when an accepting outcome has none
 */
public record Inspection(InspectionOutcome outcome, String note) {}
