package com.ecomm.checkoutpricing.domain;

import com.ecomm.commons.money.Money;

/** An Order that Order Management placed, and its total: what the Customer's Payment is for. */
public record PlacedOrder(String id, Money total) {}
