# Promotions

Coupons and Campaigns, and what a Coupon takes off at checkout. Built from
[`platform/service-template`](../../platform/service-template/README.md), so its layout, security
and testing conventions apply here. Data lives in the `promotions` database on the shared Postgres,
with the schema under Flyway ([`db/migration`](./src/main/resources/db/migration)).

A Checkout Session takes one Coupon. Staff manage Campaigns, but Checkout doesn't apply them yet;
evaluating every Discount a Checkout Session gets, Campaigns and Coupon together, is a later Sprint 3
ticket. Usage limits and redemption tracking arrive in Sprint 6 (see the
[roadmap](../../docs/roadmap.md)).

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

## Campaigns

A Campaign is a Discount Staff run for every qualifying Checkout Session, with no code. It has:

- an **`id`**, a UUID Promotions gives it;
- a **`name`**: 1 to 100 characters, trimmed;
- a **`discount`**, as a Coupon's;
- optional **`categories`**: the slugs of the Catalog Categories it is limited to. With none, it
  applies to every line;
- an optional **`minimumSubtotal`** (`Money`, zero or more);
- **`validFrom`** and **`validUntil`**, as a Coupon's;
- **`active`**: whether Staff have it switched on;
- a **`priority`**: a whole number, 0 or more, that no other Campaign has. Campaigns apply in order
  of priority, lowest first.

Each Campaign is answered with its **`state`** at Promotions' clock:

| `state` | When |
|---|---|
| `off` | it isn't `active`, whatever its window |
| `scheduled` | it is active, before `validFrom` |
| `running` | it is active, from `validFrom` up to, but not including, `validUntil` |
| `over` | it is active, at `validUntil` or later |

```json
{"id": "b4a50413-a849-4b74-82ee-aad41f355bc6", "name": "Audio week",
 "discount": {"type": "PERCENT_OFF", "percentOff": 15, "amountOff": null},
 "categories": ["audio"], "minimumSubtotal": null,
 "validFrom": "2026-10-03T18:50:04Z", "validUntil": "2027-10-03T18:50:04Z",
 "active": true, "priority": 10, "state": "running"}
```

### Checked against Catalog

A Campaign's Categories must be ones Catalog has, and its `amountOff` and `minimumSubtotal` in
currencies Catalog prices in. Promotions reads Catalog's public `GET /categories` and
`GET /currencies` on each create and update, with no token. An update checks only the Categories and
currencies the Campaign didn't already have, so one Catalog has since dropped doesn't stop Staff
switching the Campaign off or changing the rest of it. When Catalog can't be reached, the Campaign
isn't saved, and the answer is a 503.

## Seed

A fresh database has one demo Coupon and one demo Campaign, each valid for a year from when the
database was created:

- `WELCOME10`: 10% off, no minimum, active (`V2__seed_welcome10.sql`);
- "Audio week": 15% off lines in the `audio` Category, no minimum, active, priority 10
  (`V4__seed_audio_week.sql`).

Flyway adds "Audio week" to an existing database too. `make seed-reset` recreates the database,
and so gives both a fresh year.

## API

| Endpoint | Called by | |
|---|---|---|
| `GET /coupons` | Staff | Every Coupon, by code |
| `POST /coupons` | Staff | Creates a Coupon; 201 with it and its URL in `Location`; 409 when the code is taken, in any case |
| `GET /coupons/{code}` | Staff | The Coupon; 404 for an unknown code |
| `PUT /coupons/{code}` | Staff | Replaces every field but the code, which the body may leave out or repeat in any case; 404 for an unknown code |
| `DELETE /coupons/{code}` | Staff | 204; 404 for an unknown code |
| `GET /campaigns` | Staff | Every Campaign by priority, each with its `state` |
| `POST /campaigns` | Staff | Creates a Campaign under a new ID; 201 with it and its URL in `Location` |
| `GET /campaigns/{id}` | Staff | The Campaign; 404 for an unknown ID, or one that isn't a UUID |
| `PUT /campaigns/{id}` | Staff | Replaces every field but the ID; 404 for an unknown ID |
| `DELETE /campaigns/{id}` | Staff | 204; 404 for an unknown ID |
| `POST /discounts/evaluate` | Checkout | What a Coupon takes off a subtotal |

Coupons and Campaigns are Staff-only: a `CUSTOMER` or `CHECKOUT` token gets 403, no token 401. Staff
manage both in the [Admin Console](../../frontend/admin-console/README.md#promotions).

### Errors

Every error is a problem detail. A Coupon, a Campaign or an evaluation request with a missing or
malformed field is a 400 that names the field in `errors`, as Catalog's do, so the Admin Console
shows the message beside the field:

```json
{"status": 400, "detail": "discount.percentOff must be an integer from 1 to 100",
 "errors": [{"field": "discount.percentOff", "message": "must be an integer from 1 to 100"}]}
```

A field is named by its path in the JSON: `name`, `code`, `discount`, `discount.type`,
`discount.percentOff`, `discount.amountOff`, `discount.amountOff.amountMinor`,
`discount.amountOff.currency`, `categories`, `categories[1]`, `minimumSubtotal`,
`minimumSubtotal.amountMinor`, `minimumSubtotal.currency`, `validFrom`, `validUntil`, `active`,
`priority`, `couponCode` or `subtotal`. A Campaign's Category Catalog doesn't have, or the same
Category twice, is `categories[i]`, by its place in the list sent. A priority another Campaign has is
`priority`. The first field at fault is named.

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
currency, is a 400 naming `couponCode` or `subtotal`.

[`http/promotions.http`](./http/promotions.http) exercises every endpoint against the compose stack.

## Run it

```sh
docker compose up -d --build promotions   # from the repo root; listens on localhost:8087
```

That host port bypasses the API gateway, for development only. Through the gateway, Staff reach
`/api/promotions/coupons` and `/api/promotions/campaigns`, and `/api/promotions/discounts/evaluate`
is a 404. Outside Docker, Promotions finds Catalog on its host port, 8081
(`ecomm.promotions.catalog-url`).
