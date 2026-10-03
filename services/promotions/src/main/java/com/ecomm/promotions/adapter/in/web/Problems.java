package com.ecomm.promotions.adapter.in.web;

import com.ecomm.promotions.domain.InvalidPromotionException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

/** Problem details shared by the controllers. */
final class Problems {

  private Problems() {}

  /** A 400 naming the field at fault in {@code errors}, the shape Catalog's violations take. */
  static ProblemDetail invalid(InvalidPromotionException e) {
    var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    problem.setProperty("errors", List.of(e.violation()));
    return problem;
  }
}
