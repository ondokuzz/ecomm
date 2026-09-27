package com.ecomm.checkoutpricing.adapter.out.http;

import com.ecomm.checkoutpricing.application.port.out.InventoryPort;
import com.ecomm.checkoutpricing.domain.CartLine;
import com.ecomm.checkoutpricing.domain.OutOfStockException;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/**
 * Inventory's {@code POST /stock/decrement}, called with Checkout's token. A 409 (not enough Stock)
 * or a 404 (a Variant Inventory doesn't stock) means the Cart can't be sold as it is.
 */
@Component
class InventoryClient implements InventoryPort {

  private final RestClient http;

  InventoryClient(@Qualifier("inventoryRestClient") RestClient http) {
    this.http = http;
  }

  private record DecrementBody(List<Item> items) {}

  private record Item(String variantId, int quantity) {}

  @Override
  public void decrement(List<CartLine> lines) {
    var body =
        new DecrementBody(lines.stream().map(l -> new Item(l.variantId(), l.quantity())).toList());
    Downstream.run(
        "Inventory",
        () -> {
          try {
            http.post().uri("/stock/decrement").body(body).retrieve().toBodilessEntity();
          } catch (HttpClientErrorException.Conflict e) {
            throw new OutOfStockException(variantIds(e, "insufficientStock", lines));
          } catch (HttpClientErrorException.NotFound e) {
            throw new OutOfStockException(variantIds(e, "unknownVariants", lines));
          }
        });
  }

  /** The Variants Inventory's problem detail names; all of the Cart's when it names none. */
  private static List<String> variantIds(
      HttpClientErrorException error, String property, List<CartLine> lines) {
    try {
      var problem = error.getResponseBodyAs(Map.class);
      if (problem != null && problem.get(property) instanceof List<?> ids && !ids.isEmpty()) {
        return ids.stream().map(String::valueOf).toList();
      }
    } catch (RuntimeException ignored) {
      // An unreadable body names no Variants.
    }
    return lines.stream().map(CartLine::variantId).toList();
  }
}
