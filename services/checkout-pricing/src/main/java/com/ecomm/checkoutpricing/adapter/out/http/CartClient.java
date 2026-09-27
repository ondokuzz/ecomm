package com.ecomm.checkoutpricing.adapter.out.http;

import com.ecomm.checkoutpricing.application.port.out.CartPort;
import com.ecomm.checkoutpricing.domain.CartLine;
import com.ecomm.checkoutpricing.domain.Customer;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Cart's {@code /cart}, called with the Customer's own token, since Cart is theirs. */
@Component
class CartClient implements CartPort {

  private final RestClient http;

  CartClient(@Qualifier("cartRestClient") RestClient http) {
    this.http = http;
  }

  private record CartBody(List<Item> items) {}

  private record Item(String variantId, int quantity) {}

  @Override
  public List<CartLine> lines(Customer customer) {
    var cart =
        Downstream.call(
            "Cart",
            () ->
                http.get()
                    .uri("/cart")
                    .headers(h -> h.setBearerAuth(customer.accessToken()))
                    .retrieve()
                    .body(CartBody.class));
    if (cart == null || cart.items() == null) {
      return List.of();
    }
    return cart.items().stream().map(i -> new CartLine(i.variantId(), i.quantity())).toList();
  }

  @Override
  public void clear(Customer customer) {
    Downstream.run(
        "Cart",
        () ->
            http.delete()
                .uri("/cart")
                .headers(h -> h.setBearerAuth(customer.accessToken()))
                .retrieve()
                .toBodilessEntity());
  }
}
