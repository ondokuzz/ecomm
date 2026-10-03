package com.ecomm.promotions.adapter.in.web;

import com.ecomm.promotions.application.port.in.ManageCouponsUseCase;
import com.ecomm.promotions.domain.CouponAlreadyExistsException;
import com.ecomm.promotions.domain.CouponNotFoundException;
import com.ecomm.promotions.domain.InvalidPromotionException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

/** Only Staff see or change Coupons. A code in the path is matched whatever its case. */
@RestController
@RequestMapping("/coupons")
@PreAuthorize("hasRole('STAFF')")
class CouponController {

  private static final Logger log = LoggerFactory.getLogger(CouponController.class);

  private final ManageCouponsUseCase coupons;

  CouponController(ManageCouponsUseCase coupons) {
    this.coupons = coupons;
  }

  @GetMapping
  List<CouponResponse> coupons() {
    return coupons.coupons().stream().map(CouponResponse::of).toList();
  }

  @GetMapping("/{code}")
  CouponResponse coupon(@PathVariable String code) {
    return coupons
        .coupon(code)
        .map(CouponResponse::of)
        .orElseThrow(() -> new CouponNotFoundException(code));
  }

  /** 201 with the Coupon, its code upper-cased, and its URL in {@code Location}. */
  @PostMapping
  ResponseEntity<CouponResponse> create(@RequestBody CouponRequest request) {
    var coupon = coupons.create(request.toCoupon());
    log.info("Created Coupon {}", coupon.code());
    var location =
        UriComponentsBuilder.fromPath("/coupons/{code}")
            .buildAndExpand(coupon.code())
            .encode()
            .toUri();
    return ResponseEntity.created(location).body(CouponResponse.of(coupon));
  }

  /** Replaces every field of the Coupon but its code. */
  @PutMapping("/{code}")
  CouponResponse update(@PathVariable String code, @RequestBody CouponRequest request) {
    var coupon = coupons.update(request.toCouponWithCode(code));
    log.info("Updated Coupon {}", coupon.code());
    return CouponResponse.of(coupon);
  }

  @DeleteMapping("/{code}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void delete(@PathVariable String code) {
    coupons.delete(code);
    log.info("Deleted Coupon {}", code);
  }

  @ExceptionHandler(CouponNotFoundException.class)
  ProblemDetail notFound(CouponNotFoundException e) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
  }

  @ExceptionHandler(CouponAlreadyExistsException.class)
  ProblemDetail conflict(CouponAlreadyExistsException e) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
  }

  @ExceptionHandler(InvalidPromotionException.class)
  ProblemDetail invalid(InvalidPromotionException e) {
    return Problems.invalid(e);
  }
}
