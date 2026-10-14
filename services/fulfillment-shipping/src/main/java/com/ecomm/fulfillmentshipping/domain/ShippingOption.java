package com.ecomm.fulfillmentshipping.domain;

import com.ecomm.commons.money.Money;

/**
 * A way a Carrier offers to ship an Order, as a quote answers it.
 *
 * @param id such as {@code STANDARD} or {@code EXPRESS}, unique within a quote
 * @param carrier the Carrier's name
 * @param cost what the Customer pays for it, the Shipping cost
 * @param estimatedDays working days from dispatch to delivery; null on a Shipment whose Order
 *     didn't keep them
 */
public record ShippingOption(String id, String carrier, Money cost, Integer estimatedDays) {}
