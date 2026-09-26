package com.ecomm.payment.domain;

/** A payment gateway's approval, identified by the gateway's own reference for it. */
public record GatewayAuthorization(String reference) {}
