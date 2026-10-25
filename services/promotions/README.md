# Promotions

Coupons and Campaigns, and every Discount they give a Checkout Session. Built from
[`platform/service-template`](../../platform/service-template/README.md), so its layout, security
and testing conventions apply here. Data lives in the `promotions` database on the shared Postgres,
with the schema under Flyway ([`db/migration`](./src/main/resources/db/migration)).

A Checkout Session gets every running Campaign it qualifies for by itself, and takes one Coupon on
top ([Evaluation](#api)). A Coupon may have Usage limits: applying it holds one of its uses for
the session, and the checkout Saga redeems it once the session is paid
([Redemptions](#usage-limits-and-redemptions)).

## Coupons

A Coupon is:

- a **`code`**: 1 to 64 letters, digits, hyphens or underscores. It is stored upper-case, and matched
  whatever the case, so `welcome10` finds `WELCOME10`;
- a **`discount`**: `{"type": "PERCENT_OFF", "percentOff": 10}`, an integer from 1 to 100, or
  `{"type": "AMOUNT_OFF", "amountOff": {"amountMinor": 500, "currency": "EUR"}}`, a positive `Money`;
- an optional **`minimumSubtotal`** (`Money`, zero or more);
- **`validFrom`** and **`validUntil`**, ISO 8601 instants: it is valid from `validFrom` up to, but
  not including, `validUntil`, which must be later;
- **`active`**: whether Staff have it switched on;
- an optional **`usageLimit`**, the most Coupon redemptions it may have in all, and an optional
  **`perCustomerLimit`**, the most each Customer may redeem it: whole numbers, at least 1. Without
  them it is used without limit, as every Coupon from before Sprint 6 is.

Each Coupon is answered with its **`redemptions`**, its live Redemption **`holds`** and its
**`state`** at Promotions' clock:

| `state` | When |
|---|---|
| `off` | it isn't `active`, whatever its window |
| `scheduled` | it is active, before `validFrom` |
| `expired` | it is active, at `validUntil` or later |
| `exhausted` | it is active and within its window, but its redemptions and live holds reach `usageLimit` |
| `active` | otherwise: it applies |

```json
{"code": "SPRING25",
 "discount": {"type": "AMOUNT_OFF", "percentOff": null,
              "amountOff": {"amountMinor": 2500, "currency": "EUR"}},
 "minimumSubtotal": {"amountMinor": 10000, "currency": "EUR"},
 "validFrom": "2026-03-01T00:00:00Z", "validUntil": "2026-06-01T00:00:00Z", "active": true,
 "usageLimit": 100, "perCustomerLimit": 1, "redemptions": 40, "holds": 2, "state": "active"}
```

A validity window is `[validFrom, validUntil)` in UTC, to the second: a Coupon valid until
`2027-01-01T00:00:00Z` applies at `2026-12-31T23:59:59Z` and is `expired` a second later. Campaigns'
windows are the same.

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

### Usage limits and redemptions

A Checkout Session holds one use of its Coupon while it lasts, and redeems it once it is paid.
Both are internal, keyed by the session's ID, and the gateway never routes them:

| Endpoint | Called by | |
|---|---|---|
| `POST /redemptions/holds` | Checkout | `{"sessionId", "customerId", "couponCode", "expiresAt"}`: holds a use of the Coupon for the session until `expiresAt`, the session's own expiry, in place of any Coupon it held; 204 |
| `DELETE /redemptions/holds/{sessionId}` | Checkout | Releases the session's hold; 204 whether or not it had one |
| `POST /redemptions` | Orchestration | `{"sessionId", "customerId", "couponCode"}`: redeems the Coupon for the paid session; 204 |

- **Holding.** The Coupon's redemptions plus its live holds, by every other session, are weighed
  against its limits, the Coupon locked meanwhile so two sessions can't both take its last use. A
  hold is refused as a 422 with `reason` `exhausted` when they reach `usageLimit`, or else
  `alreadyRedeemed` when the Customer's own reach `perCustomerLimit`, and `unknown` when there is no
  such Coupon. A refusal leaves the session's hold as it was. Holding again for the same session
  doesn't count its own hold, and holding another Coupon for it releases the first. Holding checks
  only the limits; whether the Coupon applies at all is evaluation's to say.
- **Lapsing.** A hold counts until its `expiresAt` and nothing from then on, so a session that
  expires unpaid frees its use by itself.
- **Redeeming.** The session's hold becomes a redemption. With no hold, from a session priced before
  Usage limits or one whose hold lapsed while its Saga ran, the redemption is recorded anyway, since
  paying honours the Discount the session was priced with, as it does for a Coupon that has since
  expired, been switched off, been used up or been deleted. **So `redemptions` can pass
  `usageLimit`** by the Sagas that ran past their hold. A session redeems once however often it is
  asked, and redeeming is never refused, so the Saga's retries always end.
- **Changing limits.** Lowering a limit below what is already used keeps every hold and redemption
  made and refuses new holds; raising it lets the Coupon be held again. A redemption is never given
  back, even when the Order is later `Cancelled` or `Returned`.
- **Deleting** a Coupon deletes its holds but keeps its redemptions, so a code made again still
  counts them: a "once per Customer" Coupon can't be reset by deleting it.

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
| `GET /coupons` | Staff | Every Coupon, by code, each with its `redemptions`, `holds` and `state` |
| `POST /coupons` | Staff | Creates a Coupon; 201 with it and its URL in `Location`; 409 when the code is taken, in any case |
| `GET /coupons/{code}` | Staff | The Coupon; 404 for an unknown code |
| `PUT /coupons/{code}` | Staff | Replaces every field but the code, which the body may leave out or repeat in any case; 404 for an unknown code |
| `DELETE /coupons/{code}` | Staff | 204; 404 for an unknown code |
| `GET /campaigns` | Staff | Every Campaign by priority, each with its `state` |
| `POST /campaigns` | Staff | Creates a Campaign under a new ID; 201 with it and its URL in `Location` |
| `GET /campaigns/{id}` | Staff | The Campaign; 404 for an unknown ID, or one that isn't a UUID |
| `PUT /campaigns/{id}` | Staff | Replaces every field but the ID; 404 for an unknown ID |
| `DELETE /campaigns/{id}` | Staff | 204; 404 for an unknown ID |
| `POST /discounts/evaluate` | Checkout | Every Discount a Checkout Session's lines are due: the running Campaigns', then a Coupon's |
| `POST /redemptions/holds`, `DELETE /redemptions/holds/{sessionId}` | Checkout | A session's Redemption hold ([above](#usage-limits-and-redemptions)) |
| `POST /redemptions` | Orchestration | A paid session's Coupon redemption |

Coupons and Campaigns are Staff-only: a `CUSTOMER` or `CHECKOUT` token gets 403, no token 401. Only
`CHECKOUT` may hold and release, and only `ORCHESTRATION` redeem; any other token gets 403. Staff
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
`priority`, `usageLimit`, `perCustomerLimit`, `couponCode`, `lines`, or a line's field such as
`lines[1].quantity`; for holds and redemptions, `sessionId`, `customerId`, `couponCode` and
`expiresAt`. A Campaign's Category Catalog doesn't have, or the same
Category twice, is `categories[i]`, by its place in the list sent. A priority another Campaign has is
`priority`. The first field at fault is named.

Evaluation is internal: only Checkout calls it, with its own token and the `CHECKOUT` role
([ADR 0002](../identity-access/docs/adr/0002-service-identity-by-client-credentials.md)), and the
[API gateway](../../platform/api-gateway/README.md) never routes it, so nobody can probe Coupon codes
outside a checkout. It takes a Checkout Session's lines, each with its Product's SKU and Category and
the unit Price captured when the session started, and the Coupon code applied, if any:

```json
{"lines": [{"variantId": "PHN-PIXEL-9-OBSIDIAN-128", "sku": "PHN-PIXEL-9", "category": "phones",
            "quantity": 2, "unitPrice": {"amountMinor": 79900, "currency": "EUR"}},
           {"variantId": "AUD-AIRPODS-PRO-2", "sku": "AUD-AIRPODS-PRO-2", "category": "audio",
            "quantity": 1, "unitPrice": {"amountMinor": 27900, "currency": "EUR"}}],
 "couponCode": "welcome10"}
```

and answers with every Discount they are due, in the order they apply. A `CAMPAIGN` one names the
Campaign's ID and name, a `COUPON` one the Coupon's upper-case code:

```json
{"discounts": [
  {"source": "CAMPAIGN", "campaignId": "b4a50413-a849-4b74-82ee-aad41f355bc6",
   "campaignName": "Audio week", "amount": {"amountMinor": 4185, "currency": "EUR"}},
  {"source": "COUPON", "couponCode": "WELCOME10",
   "amount": {"amountMinor": 18351, "currency": "EUR"}}]}
```

The Discounts stack (`DiscountStacking`):

- **Campaigns first, by priority.** Every Campaign running at Promotions' clock applies, lowest
  priority first, each worked out on what its lines still come to after the Discounts before it.
- **Lines and minimums.** A Campaign limited to Categories discounts only the lines in them, and
  measures its minimum on them; one without Categories takes all the lines. A minimum is measured on
  what the lines came to before any Discount, so an earlier Campaign never takes one away: the
  Customer who reaches it is given it. A Campaign is left out when none of its lines are in the session, its minimum isn't reached,
  its fixed amount or minimum is in another currency, or it would take nothing off.
- **Spread over its lines.** What a Campaign takes off is shared over its lines in proportion to
  what each still comes to, rounded down with the minor units left over going to the lines that lost
  the most, so a later Campaign sees exactly what is left of each.
- **The Coupon last.** It applies on what all the lines still come to, its minimum measured on the
  subtotal before any Discount, and is rejected just as it would be on its own: a Coupon that doesn't apply is a 422 problem detail whose
  `reason` says why (see the table above), whatever the Campaigns gave.
- **Rounding and the cap.** Percentages round down to the minor unit, and each Discount is at most
  what its lines still come to, so together they never exceed the subtotal.

Every line must be in one currency, with at least one line; a missing or malformed field is a 400
naming it, such as `lines[1].quantity`. Without a `couponCode`, the answer holds only the Campaigns'
Discounts, possibly none.

[`http/promotions.http`](./http/promotions.http) exercises every endpoint against the compose stack.

## Run it

```sh
docker compose up -d --build promotions   # from the repo root; listens on localhost:8087
```

That host port bypasses the API gateway, for development only. Through the gateway, Staff reach
`/api/promotions/coupons` and `/api/promotions/campaigns`, and `/api/promotions/discounts/evaluate`
and `/api/promotions/redemptions/**` are 404s. Outside Docker, Promotions finds Catalog on its host port, 8081
(`ecomm.promotions.catalog-url`).

Since Sprint 5 its database password is a Compose secret, mounted as the file
`spring.datasource.password` (see the root README's "Secrets"); `application.yml` has none. Outside
Docker, give it as `SPRING_DATASOURCE_PASSWORD="$(cat .secrets/ecomm/postgres-password)"`.

## Tests

`./gradlew :services:promotions:check`. The application runs against Postgres in Testcontainers,
with WireMock standing in for Catalog and a clock the tests set (`TimeSource`). `RedemptionApiTest`
covers holds against each limit, `exhausted` and `alreadyRedeemed`, holding again or another
Coupon for the same session, a lapsed hold freeing its use, release, redeeming with and without a
hold and once per session, a Coupon ended or switched off while held still redeemed, lowering and
raising limits, Coupon state, deleting a Coupon keeping its redemptions, and who may call what.
`EvaluateDiscountApiTest` pins the window's edges to the second. `CouponLimitsTest` is pure JUnit
for the limit and hold arithmetic and Coupon state, a deliberate exception to testing at the HTTP
seam, as `DiscountStackingTest` and `DiscountCalculationTest` are for the Discounts.
