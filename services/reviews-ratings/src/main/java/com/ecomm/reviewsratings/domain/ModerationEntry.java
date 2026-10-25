package com.ecomm.reviewsratings.domain;

import java.time.Instant;
import java.util.Optional;

/**
 * One entry of a review's moderation history: what a Staff member did, when, and why if they said.
 */
public record ModerationEntry(
    ModerationAction action, String staffId, Instant at, Optional<String> reason) {}
