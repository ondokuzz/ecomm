package com.ecomm.payment.application.port.in;

import java.time.Instant;

/**
 * Which of a Customer's Payment transactions they want to see: those on their Payments that
 * happened from {@code from} up to, but not including, {@code to}. A bound left null leaves that
 * end open.
 */
public record TransactionHistoryFilter(String customerId, Instant from, Instant to) {}
