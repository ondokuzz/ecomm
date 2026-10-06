package com.ecomm.reviewsratings.domain;

import java.util.List;

/** One page of a Product's reviews, newest first, counting pages from 0. */
public record ReviewPage(List<Review> items, int page, int size, long total) {}
