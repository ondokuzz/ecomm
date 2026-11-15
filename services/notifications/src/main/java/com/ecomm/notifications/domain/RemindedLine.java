package com.ecomm.notifications.domain;

import com.ecomm.commons.money.Money;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * One line of a Cart reminder: the Variant's name, how many, its Price now, whether it is in Stock
 * now, and whether its Price changed after the Cart was last changed.
 */
public record RemindedLine(
    String name, int quantity, Money price, boolean inStock, boolean priceChanged) {

  static List<Map<String, Object>> model(List<RemindedLine> lines, Locale locale) {
    return lines.stream()
        .map(
            l ->
                Map.<String, Object>of(
                    "name", l.name(),
                    "quantity", l.quantity(),
                    "price", Formats.money(l.price(), locale),
                    "outOfStock", !l.inStock(),
                    "priceChanged", l.priceChanged()))
        .toList();
  }
}
