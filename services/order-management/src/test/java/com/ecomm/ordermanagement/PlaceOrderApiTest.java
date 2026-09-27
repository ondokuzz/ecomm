package com.ecomm.ordermanagement;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;

/** Checkout places an Order for a Customer, from the Order Lines it priced. */
class PlaceOrderApiTest extends OrderApiTest {

  @Test
  void anOrderIsPlacedWithItsLinesAndTotal() {
    var order = placed();

    assertThat(order.id()).isNotBlank();
    assertThat(order.status()).isEqualTo("PLACED");
    assertThat(order.lines())
        .containsExactly(
            new LineView("PHN-PIXEL-9", 2, new AmountView(79900, "EUR")),
            new LineView("AUD-AIRPODS-PRO-2", 1, new AmountView(14950, "EUR")));
    assertThat(order.total()).isEqualTo(new AmountView(174750, "EUR"));
    assertThat(order.placedAt()).isNotBlank();
  }

  @Test
  void aFreeLineCountsTowardsNothing() {
    var order =
        place(
                """
                {"customerId": "customer-42", "lines": [
                  {"variantId": "PHN-PIXEL-9", "quantity": 1,
                   "unitPrice": {"amountMinor": 79900, "currency": "EUR"}},
                  {"variantId": "ACC-CASE", "quantity": 3,
                   "unitPrice": {"amountMinor": 0, "currency": "EUR"}}
                ]}
                """)
            .expectStatus()
            .isCreated()
            .expectBody(OrderView.class)
            .returnResult()
            .getResponseBody();

    assertThat(order.total()).isEqualTo(new AmountView(79900, "EUR"));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        """
        {"customerId": "customer-42"}
        """,
        """
        {"customerId": "customer-42", "lines": []}
        """,
        """
        {"customerId": "customer-42", "lines": [null]}
        """,
        """
        {"customerId": "customer-42", "lines": [{"quantity": 1, "unitPrice": {"amountMinor": 100, "currency": "EUR"}}]}
        """,
        """
        {"customerId": "customer-42", "lines": [{"variantId": " ", "quantity": 1,
                    "unitPrice": {"amountMinor": 100, "currency": "EUR"}}]}
        """,
        """
        {"customerId": "customer-42", "lines": [{"variantId": 7, "quantity": 1,
                    "unitPrice": {"amountMinor": 100, "currency": "EUR"}}]}
        """,
        """
        {"customerId": "customer-42", "lines": [{"variantId": "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx",
                    "quantity": 1, "unitPrice": {"amountMinor": 100, "currency": "EUR"}}]}
        """,
        """
        {"customerId": "customer-42", "lines": [{"variantId": "V-1", "unitPrice": {"amountMinor": 100, "currency": "EUR"}}]}
        """,
        """
        {"customerId": "customer-42", "lines": [{"variantId": "V-1", "quantity": 0,
                    "unitPrice": {"amountMinor": 100, "currency": "EUR"}}]}
        """,
        """
        {"customerId": "customer-42", "lines": [{"variantId": "V-1", "quantity": 1.5,
                    "unitPrice": {"amountMinor": 100, "currency": "EUR"}}]}
        """,
        """
        {"customerId": "customer-42", "lines": [{"variantId": "V-1", "quantity": "2",
                    "unitPrice": {"amountMinor": 100, "currency": "EUR"}}]}
        """,
        """
        {"customerId": "customer-42", "lines": [{"variantId": "V-1", "quantity": 1}]}
        """,
        """
        {"customerId": "customer-42", "lines": [{"variantId": "V-1", "quantity": 1,
                    "unitPrice": {"amountMinor": -1, "currency": "EUR"}}]}
        """,
        """
        {"customerId": "customer-42", "lines": [{"variantId": "V-1", "quantity": 1,
                    "unitPrice": {"amountMinor": 1.5, "currency": "EUR"}}]}
        """,
        """
        {"customerId": "customer-42", "lines": [{"variantId": "V-1", "quantity": 1, "unitPrice": {"amountMinor": 100}}]}
        """,
        """
        {"customerId": "customer-42", "lines": [{"variantId": "V-1", "quantity": 1,
                    "unitPrice": {"amountMinor": 100, "currency": "XYZ"}}]}
        """,
        """
        {"customerId": "customer-42", "lines": [
          {"variantId": "V-1", "quantity": 1, "unitPrice": {"amountMinor": 100, "currency": "EUR"}},
          {"variantId": "V-1", "quantity": 2, "unitPrice": {"amountMinor": 100, "currency": "EUR"}}
        ]}
        """,
        """
        {"customerId": "customer-42", "lines": [
          {"variantId": "V-1", "quantity": 1, "unitPrice": {"amountMinor": 100, "currency": "EUR"}},
          {"variantId": "V-2", "quantity": 1, "unitPrice": {"amountMinor": 100, "currency": "USD"}}
        ]}
        """,
        """
        {"customerId": "customer-42", "lines": [{"variantId": "V-1", "quantity": 2,
                    "unitPrice": {"amountMinor": 9223372036854775807, "currency": "EUR"}}]}
        """,
        """
        {"customerId": "customer-42", "lines": [
          {"variantId": "V-1", "quantity": 1,
           "unitPrice": {"amountMinor": 9223372036854775807, "currency": "EUR"}},
          {"variantId": "V-2", "quantity": 1, "unitPrice": {"amountMinor": 1, "currency": "EUR"}}
        ]}
        """,
        """
        {"lines": [{"variantId": "V-1", "quantity": 1,
                    "unitPrice": {"amountMinor": 100, "currency": "EUR"}}]}
        """,
        """
        {"customerId": " ", "lines": [{"variantId": "V-1", "quantity": 1,
                                       "unitPrice": {"amountMinor": 100, "currency": "EUR"}}]}
        """,
        """
        {"customerId": 42, "lines": [{"variantId": "V-1", "quantity": 1,
                                      "unitPrice": {"amountMinor": 100, "currency": "EUR"}}]}
        """,
        "{\"customerId\": \"cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc\", \"lines\": [{\"variantId\": \"V-1\", \"quantity\": 1, \"unitPrice\": {\"amountMinor\": 100, \"currency\": \"EUR\"}}]}",
        "not json"
      })
  void anInvalidOrderIsABadRequest(String body) {
    place(body)
        .expectStatus()
        .isBadRequest()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON);
  }
}
