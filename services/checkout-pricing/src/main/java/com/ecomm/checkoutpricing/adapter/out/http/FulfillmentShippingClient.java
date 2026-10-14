package com.ecomm.checkoutpricing.adapter.out.http;

import com.ecomm.checkoutpricing.application.port.in.ServiceTokenUnavailableException;
import com.ecomm.checkoutpricing.application.port.in.ShippingUnavailableException;
import com.ecomm.checkoutpricing.application.port.out.ShippingQuotes;
import com.ecomm.checkoutpricing.domain.PricedLine;
import com.ecomm.checkoutpricing.domain.ShippingAddress;
import com.ecomm.checkoutpricing.domain.ShippingOption;
import com.ecomm.commons.money.Money;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Fulfillment & Shipping's internal {@code POST /shipping/quotes}, called with Checkout's token. A
 * 422 with {@code reason} {@code shippingUnavailable} means the Carrier serves no option there. Any
 * other failure, or an answer that couldn't be right, such as an option in another currency, means
 * it can't quote.
 */
@Component
class FulfillmentShippingClient implements ShippingQuotes {

  private final RestClient http;

  FulfillmentShippingClient(@Qualifier("fulfillmentShippingRestClient") RestClient http) {
    this.http = http;
  }

  private record QuoteBody(AddressBody destination, List<LineBody> lines, String currency) {}

  /** An address's optional fields are left out when it has none. */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private record AddressBody(
      String recipientName,
      String line1,
      String line2,
      String postcode,
      String city,
      String region,
      String country,
      String phone) {

    static AddressBody of(ShippingAddress a) {
      return new AddressBody(
          a.recipientName(),
          a.line1(),
          a.line2(),
          a.postcode(),
          a.city(),
          a.region(),
          a.country(),
          a.phone());
    }
  }

  private record LineBody(String variantId, int quantity) {}

  private record AnswerBody(String warehouseId, List<OptionBody> options) {}

  private record OptionBody(String id, String carrier, Amount cost, Integer estimatedDays) {}

  private record Amount(Long amountMinor, String currency) {}

  @Override
  public List<ShippingOption> quote(
      ShippingAddress destination, List<PricedLine> lines, Currency currency) {
    var body =
        new QuoteBody(
            AddressBody.of(destination),
            lines.stream().map(l -> new LineBody(l.variantId(), l.quantity())).toList(),
            currency.getCurrencyCode());
    AnswerBody answer;
    try {
      answer = http.post().uri("/shipping/quotes").body(body).retrieve().body(AnswerBody.class);
    } catch (HttpClientErrorException.UnprocessableContent e) {
      if ("shippingUnavailable".equals(reason(e))) {
        return List.of();
      }
      throw new ShippingUnavailableException("Fulfillment & Shipping refused the quote", e);
    } catch (OAuth2AuthorizationException e) {
      throw new ServiceTokenUnavailableException(e);
    } catch (RestClientException e) {
      throw new ShippingUnavailableException("Fulfillment & Shipping failed: " + e.getMessage(), e);
    }
    return checked(answer, currency);
  }

  /** The options, once each is seen to have an ID, a Carrier, a cost in the currency and days. */
  private static List<ShippingOption> checked(AnswerBody answer, Currency currency) {
    if (answer == null || answer.options() == null) {
      throw invalid("no options");
    }
    var options = new ArrayList<ShippingOption>();
    for (var o : answer.options()) {
      if (o == null
          || blank(o.id())
          || blank(o.carrier())
          || o.cost() == null
          || o.cost().amountMinor() == null
          || o.cost().amountMinor() < 0
          || !currency.getCurrencyCode().equals(o.cost().currency())
          || o.estimatedDays() == null
          || o.estimatedDays() < 0) {
        throw invalid("an option without an ID, a Carrier, a cost in " + currency + " or days");
      }
      options.add(
          new ShippingOption(
              o.id(), o.carrier(), new Money(o.cost().amountMinor(), currency), o.estimatedDays()));
    }
    return List.copyOf(options);
  }

  private static boolean blank(String value) {
    return value == null || value.isBlank();
  }

  private static ShippingUnavailableException invalid(String what) {
    return new ShippingUnavailableException("Fulfillment & Shipping answered " + what, null);
  }

  /** The {@code reason} of Fulfillment & Shipping's problem detail, if it gave one. */
  private static String reason(HttpClientErrorException error) {
    try {
      var problem = error.getResponseBodyAs(Map.class);
      return problem != null && problem.get("reason") instanceof String reason ? reason : null;
    } catch (RuntimeException ignored) {
      return null;
    }
  }
}
