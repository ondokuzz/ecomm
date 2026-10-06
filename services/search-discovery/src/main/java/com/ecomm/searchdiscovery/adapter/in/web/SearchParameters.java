package com.ecomm.searchdiscovery.adapter.in.web;

import com.ecomm.searchdiscovery.domain.InvalidSearchException;
import com.ecomm.searchdiscovery.domain.NumberRange;
import com.ecomm.searchdiscovery.domain.PriceRange;
import com.ecomm.searchdiscovery.domain.SearchRequest;
import com.ecomm.searchdiscovery.domain.SortOrder;
import java.math.BigDecimal;
import java.util.Currency;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;
import org.springframework.util.MultiValueMap;

/**
 * Reads a search from its query parameters:
 *
 * <ul>
 *   <li>{@code q}: the text; {@code category}: a Category's slug;
 *   <li>{@code attr.<name>=<value>}, repeated for more values: an ENUM, TEXT or BOOLEAN attribute
 *       having any of them;
 *   <li>{@code range.<name>=<min>..<max>}: a NUMBER attribute in a range, either end left open as
 *       in {@code 5..} or {@code ..10};
 *   <li>{@code price=<min>..<max>} with {@code currency}: a Variant's Price in range, in the
 *       Currency's Minor unit; {@code currency} alone keeps Products priced in it;
 *   <li>{@code inStock=true}; {@code sort}: {@code relevance}, {@code newest}, {@code price-asc} or
 *       {@code price-desc}; {@code page} from 0 and {@code size}.
 * </ul>
 */
final class SearchParameters {

  static final int DEFAULT_PAGE_SIZE = 24;

  private static final String ATTRIBUTE = "attr.";
  private static final String RANGE = "range.";

  private SearchParameters() {}

  static SearchRequest read(MultiValueMap<String, String> params) {
    var values = new LinkedHashMap<String, Set<String>>();
    var ranges = new LinkedHashMap<String, NumberRange>();
    params.forEach(
        (key, given) -> {
          if (key.startsWith(ATTRIBUTE) && key.length() > ATTRIBUTE.length()) {
            values
                .computeIfAbsent(key.substring(ATTRIBUTE.length()), k -> new LinkedHashSet<>())
                .addAll(given);
          } else if (key.startsWith(RANGE) && key.length() > RANGE.length()) {
            var bounds = Bounds.of(key, single(params, key));
            ranges.put(
                key.substring(RANGE.length()),
                new NumberRange(
                    bounds.min(SearchParameters::decimal), bounds.max(SearchParameters::decimal)));
          }
        });
    return new SearchRequest(
        single(params, "q"),
        single(params, "category"),
        values,
        ranges,
        price(single(params, "currency"), single(params, "price")),
        Boolean.parseBoolean(single(params, "inStock")),
        sort(single(params, "sort")),
        integer(params, "page", 0),
        integer(params, "size", DEFAULT_PAGE_SIZE));
  }

  private static PriceRange price(String currency, String price) {
    if (currency == null) {
      if (price != null) {
        throw new InvalidSearchException("price needs its currency");
      }
      return null;
    }
    var bounds = price == null ? new Bounds("", "") : Bounds.of("price", price);
    return new PriceRange(
        currency(currency),
        bounds.min(SearchParameters::amount),
        bounds.max(SearchParameters::amount));
  }

  private static Currency currency(String code) {
    try {
      return Currency.getInstance(code);
    } catch (IllegalArgumentException e) {
      throw new InvalidSearchException("currency must be an ISO 4217 code, such as EUR");
    }
  }

  /** The ends of a {@code <min>..<max>} range, as written; an end left out is null. */
  private record Bounds(String min, String max) {

    static Bounds of(String name, String value) {
      var dots = value.indexOf("..");
      if (dots < 0) {
        throw new InvalidSearchException(name + " must be a range, such as 5..10, 5.. or ..10");
      }
      return new Bounds(value.substring(0, dots), value.substring(dots + 2));
    }

    <T> T min(Function<String, T> read) {
      return min.isEmpty() ? null : read.apply(min);
    }

    <T> T max(Function<String, T> read) {
      return max.isEmpty() ? null : read.apply(max);
    }
  }

  private static BigDecimal decimal(String value) {
    try {
      return new BigDecimal(value);
    } catch (NumberFormatException e) {
      throw new InvalidSearchException("a range's ends must be numbers, such as 7.5");
    }
  }

  private static Long amount(String value) {
    try {
      var amount = Long.parseLong(value);
      if (amount < 0) {
        throw new NumberFormatException();
      }
      return amount;
    } catch (NumberFormatException e) {
      throw new InvalidSearchException("price's ends must be whole amounts in the Minor unit");
    }
  }

  private static SortOrder sort(String value) {
    if (value == null) {
      return SortOrder.RELEVANCE;
    }
    try {
      return SortOrder.valueOf(value.replace('-', '_').toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      throw new InvalidSearchException("sort must be relevance, newest, price-asc or price-desc");
    }
  }

  private static int integer(MultiValueMap<String, String> params, String name, int otherwise) {
    var value = single(params, name);
    if (value == null) {
      return otherwise;
    }
    try {
      return Integer.parseInt(value);
    } catch (NumberFormatException e) {
      throw new InvalidSearchException(name + " must be a whole number");
    }
  }

  private static String single(MultiValueMap<String, String> params, String name) {
    var values = params.get(name);
    return values == null || values.isEmpty() ? null : values.getFirst();
  }
}
