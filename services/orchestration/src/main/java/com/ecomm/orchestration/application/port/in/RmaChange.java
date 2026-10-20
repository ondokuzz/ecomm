package com.ecomm.orchestration.application.port.in;

/**
 * An RMA event, as the returns Saga is signalled with it: its version, and the status the RMA was
 * in at that version, such as {@code RECEIVED} or {@code INSPECTED}.
 */
public record RmaChange(long version, String status) {}
