package com.ecomm.fulfillmentshipping.adapter.in.web;

import com.ecomm.commons.money.Money;
import com.ecomm.fulfillmentshipping.application.port.in.QuoteShippingUseCase;
import com.ecomm.fulfillmentshipping.domain.ShippingQuote;
import com.ecomm.fulfillmentshipping.domain.ShippingUnavailableException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Checkout asks what shipping a Checkout Session costs, with its own {@code CHECKOUT} token. It is
 * internal: the gateway never routes it.
 */
@RestController
@PreAuthorize("hasRole('CHECKOUT')")
class ShippingQuoteController {

  private static final Logger log = LoggerFactory.getLogger(ShippingQuoteController.class);

  record QuoteResponse(String warehouseId, List<OptionResponse> options) {

    static QuoteResponse of(ShippingQuote quote) {
      return new QuoteResponse(
          quote.origin().id(),
          quote.options().stream()
              .map(o -> new OptionResponse(o.id(), o.carrier(), o.cost(), o.estimatedDays()))
              .toList());
    }
  }

  record OptionResponse(String id, String carrier, Money cost, int estimatedDays) {}

  private final QuoteShippingUseCase quotes;

  ShippingQuoteController(QuoteShippingUseCase quotes) {
    this.quotes = quotes;
  }

  /**
   * 200 with the Warehouse and Shipping options; 400 naming a malformed field; 422 with {@code
   * reason: shippingUnavailable} when nothing ships there in that Currency.
   */
  @PostMapping("/shipping/quotes")
  QuoteResponse quote(@RequestBody QuoteRequest request) {
    var country = request.toCountry();
    var quote = quotes.quote(country, request.toLines(), request.toCurrency());
    log.info(
        "Quoted {} Shipping options to {} from {}",
        quote.options().size(),
        country,
        quote.origin().id());
    return QuoteResponse.of(quote);
  }

  @ExceptionHandler(ShippingUnavailableException.class)
  ProblemDetail unavailable(ShippingUnavailableException e) {
    log.info(e.getMessage());
    var problem =
        ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, e.getMessage());
    problem.setProperty("reason", "shippingUnavailable");
    return problem;
  }

  @ExceptionHandler(InvalidQuoteRequestException.class)
  ProblemDetail invalid(InvalidQuoteRequestException e) {
    var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    problem.setProperty("errors", List.of(e.violation()));
    return problem;
  }
}
