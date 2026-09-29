package com.ecomm.inventory.adapter.in.web;

import com.ecomm.inventory.domain.InsufficientStockException;
import com.ecomm.inventory.domain.InvalidStockRequestException;
import com.ecomm.inventory.domain.UnknownVariantException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

/** The problem details every controller that takes a batch of Stock answers with. */
final class StockProblems {

  private StockProblems() {}

  static ProblemDetail unknownVariant(UnknownVariantException e) {
    var problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    problem.setProperty("unknownVariants", e.variantIds());
    return problem;
  }

  static ProblemDetail insufficientStock(InsufficientStockException e) {
    var problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    problem.setProperty("insufficientStock", e.variantIds());
    return problem;
  }

  static ProblemDetail invalid(InvalidStockRequestException e) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
  }
}
