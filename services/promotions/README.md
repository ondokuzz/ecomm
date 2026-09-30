# Promotions

Coupons, and what one takes off at checkout. Built from
[`platform/service-template`](../../platform/service-template/README.md), so its layout, security
and testing conventions apply here. Data lives in the `promotions` database on the shared Postgres,
with the schema under Flyway ([`db/migration`](./src/main/resources/db/migration)).

Sprint 2 has one Coupon per Checkout Session and no usage limits or redemption tracking; those, and
the campaign rules engine, arrive in Sprint 6 (see the [roadmap](../../docs/roadmap.md)).

## Coupons

A Coupon is:

- a **`code`**: 1 to 64 letters, digits, hyphens or underscores. It is stored upper-case, and matched
  whatever the case, so `welcome10` finds `WELCOME10`;
- a **`discount`**: `{"type": "PERCENT_OFF", "percentOff": 10}`, an integer from 1 to 100, or
  `{"type": "AMOUNT_OFF", "amountOff": {"amountMinor": 500, "currency": "EUR"}}`, a positive `Money`;
- an optional **`minimumSubtotal`** (`Money`, zero or more);
- **`validFrom`** and **`validUntil`**, ISO 8601 instants: it is valid from `validFrom` up to, but
  not including, `validUntil`, which must be later;
- **`active`**: whether Staff have it switched on.

```json
{"code": "SPRING25",
 "discount": {"type": "AMOUNT_OFF", "percentOff": null,
              "amountOff": {"amountMinor": 2500, "currency": "EUR"}},
 "minimumSubtotal": {"amountMinor": 10000, "currency": "EUR"},
 "validFrom": "2026-03-01T00:00:00Z", "validUntil": "2026-06-01T00:00:00Z", "active": true}
```

### Discount rules

Evaluating a Coupon against a subtotal checks, in this order, and stops at the first that fails:

| `reason` | The Coupon doesn't apply when |
|---|---|
| `unknown` | no Coupon has the code |
| `inactive` | it isn't `active` |
| `notYetValid` | it is before `validFrom` |
| `expired` | it is `validUntil` or later |
| `currencyMismatch` | its `amountOff` or its `minimumSubtotal` is in another currency than the subtotal |
| `belowMinimum` | the subtotal is less than its `minimumSubtotal` |

Otherwise its Discount is:

- for `PERCENT_OFF`, that percentage of the subtotal, rounded **down** to the currency's minor unit:
  15% of €10.01 is €1.50, and 10% of ¥1,999 is ¥199;
- for `AMOUNT_OFF`, the amount;
- either way, never more than the subtotal, so it can bring a total to zero but not below.

A `PERCENT_OFF` Coupon applies to a subtotal in any currency, unless its minimum is in another one.

### Seed

A fresh database has one demo Coupon, `WELCOME10`: 10% off, no minimum, active, valid for a year
from when the database was created (`V2__seed_welcome10.sql`). `make seed-reset` recreates the
database, and so gives it a fresh year.

## API

| Endpoint | Called by | |
|---|---|---|
| `GET /coupons` | Staff | Every Coupon, by code |
| `POST /coupons` | Staff | Creates a Coupon; 201 with it and its URL in `Location`; 409 when the code is taken, in any case |
| `GET /coupons/{code}` | Staff | The Coupon; 404 for an unknown code |
| `PUT /coupons/{code}` | Staff | Replaces every field but the code, which the body may leave out or repeat in any case; 404 for an unknown code |
| `DELETE /coupons/{code}` | Staff | 204; 404 for an unknown code |
| `POST /discounts/evaluate` | Checkout | What a Coupon takes off a subtotal |

Coupons are Staff-only: a `CUSTOMER` or `CHECKOUT` token gets 403, no token 401. There is no Coupon
screen in the Admin Console yet; Staff use this API and its `.http` file.

Evaluation is internal: only Checkout calls it, with its own token and the `CHECKOUT` role
([ADR 0002](../identity-access/docs/adr/0002-service-identity-by-client-credentials.md)), and the
[API gateway](../../platform/api-gateway/README.md) never routes it, so nobody can probe Coupon codes
outside a checkout. It takes

```json
{"couponCode": "welcome10", "subtotal": {"amountMinor": 159800, "currency": "EUR"}}
```

and answers with the Coupon's upper-case code and its Discount:

```json
{"couponCode": "WELCOME10", "discount": {"amountMinor": 15980, "currency": "EUR"}}
```

A Coupon that doesn't apply is a 422 problem detail whose `reason` says why (see the table above).
A missing or blank `couponCode`, or a subtotal that isn't a non-negative integer with a known
currency, is a 400. So is any malformed Coupon Staff send. Every error is a problem detail.

[`http/promotions.http`](./http/promotions.http) exercises every endpoint against the compose stack.

## Run it

```sh
docker compose up -d --build promotions   # from the repo root; listens on localhost:8087
```

That host port bypasses the API gateway, for development only. Through the gateway, Staff reach
`/api/promotions/coupons`, and `/api/promotions/discounts/evaluate` is a 404.
