# Reviews & Ratings

Customers rate and review what they bought. Reviews & Ratings keeps the reviews in its own MongoDB
database, `reviews` ([ADR 0001](./docs/adr/0001-reviews-on-mongodb.md)), beside a projection of
Order Management's and Catalog's integration events that tells it who bought which Product. It
publishes no events. Built from [`platform/service-template`](../../platform/service-template/README.md),
so its layout, security and testing conventions apply here.

## API

| Endpoint | Who | |
|---|---|---|
| `GET /products/{sku}/reviews` | anyone | A page of the Product's reviews, newest first |
| `GET /products/{sku}/rating-summary` | anyone | The Product's rating summary |
| `GET /rating-summaries?sku=…&sku=…` | anyone | Up to 50 Products' summaries at once |
| `GET /products/{sku}/eligibility` | `CUSTOMER` | Whether I may review the Product, and why not |
| `POST /products/{sku}/reviews` | `CUSTOMER` | Post my review: 201, 403 `notPurchased`, 409 `alreadyReviewed` |
| `PUT /reviews/{id}` | `CUSTOMER` | Edit my review; another Customer's is a 404 |
| `DELETE /reviews/{id}` | `CUSTOMER` | Delete my review: 204; another Customer's is a 404 |

[`http/reviews-ratings.http`](./http/reviews-ratings.http) exercises each against the compose stack.

### A review

```json
{"id": "6c1f…", "sku": "PHN-PIXEL-9", "author": "Demo C.", "variantId": "PHN-PIXEL-9-OBSIDIAN-256",
 "rating": 4, "title": "Great camera", "body": "Sharp photos, and the battery lasts a day.",
 "createdAt": "2026-10-06T14:02:11.204Z", "editedAt": null}
```

- `rating` is from 1 to 5. `title` is optional, up to 120 characters, and `null` when blank. `body`
  is required, up to 2,000 characters. Both are trimmed. Anything else is a 400 problem detail.
- `author` is the token's `given_name` and the first letter of its `family_name`, captured when the
  review is posted: `Demo C.`. A missing part is left out, and a token with neither is `A Customer`.
  A review never shows the Customer's ID.
- `variantId` is the Variant the Customer bought, from their latest Order that counts with one of
  the Product's Variants.
- `editedAt` is `null` until the review is first edited. Editing keeps the Variant and the author.

A page is `{"items", "page", "size", "total"}`: `page` from 0, `size` from 1 to 50, 10 by default.

### Who may review

A Customer may review a Product once they have an Order that **counts** for any of its Variants: one
that is `PAID`, `FULFILLED`, `SHIPPED` or `DELIVERED`. One only `PLACED`, or `CANCELLED` or
`RETURNED`, doesn't. They may review each Product once.

`GET /products/{sku}/eligibility` answers `{"eligible", "reason", "review"}`: `eligible` with no
`reason`, or a `reason` of `notPurchased` or `alreadyReviewed`, in which case `review` is theirs.
Posting is checked the same way, and a refused post is a problem detail with the same `reason`: a
403 for `notPurchased`, a 409 for `alreadyReviewed`. A unique index on the SKU and the Customer
settles two posts at once.

Eligibility is checked only when a review is posted. A review stays when its Order is later
cancelled or returned, and its Customer may still edit or delete it. Once deleted, they may post
another if they still have an Order that counts.

### A rating summary

```json
{"sku": "PHN-PIXEL-9", "count": 3, "average": 4.7, "perStar": {"1": 0, "2": 0, "3": 0, "4": 1, "5": 2}}
```

`average` is to one decimal, a half rounded up, and `null` without reviews. `GET /rating-summaries` takes
`sku` repeated, 1 to 50 distinct SKUs or it is a 400, and answers a list of summaries in the order
asked, a SKU asked twice once. A SKU nobody has reviewed, or one Reviews has never heard of, has a
count of 0.

## The projection

Reviews consumes two topics, each in its own consumer group, and keeps the newest version of every
aggregate it is sent; a duplicate or an older event is ignored.

| Topic | Group | Becomes |
|---|---|---|
| `order-management.order` | `reviews-ratings.orders` | A document in `orders` per Order: its Customer, Status, placement time and Variant IDs |
| `catalog.product` | `reviews-ratings.products` | A document in `products` per SKU: its Variant IDs |

Eligibility joins the two when it is asked, so an Order and its Product may arrive in either order. A
Product keeps every Variant it has ever had, so a Variant Catalog drops, or a removed Product, still
counts for those who bought it. Statuses are read as text: one Order Management adds later reads as
unknown and doesn't count, rather than failing the event. Each event's Correlation ID is in the logs while it is applied,
with a line per event: `Applied Order 0b6f… version 2`, or `Ignored …`. As with
[Search](../search-discovery/README.md#the-projection), one aggregate's events arrive on one
partition, so applying one reads and writes a single document without a transaction.

### Storage

`reviews` holds one document per review, keyed by its ID, with two indexes: `oneReviewPerCustomer`,
unique on the SKU and the Customer, and `newestFirst` on the SKU, `createdAt` and the ID. Summaries
are counted with one aggregation over the SKUs asked, grouped by SKU and rating. `orders` is
indexed by Customer.

### Rebuilding

The projection can be rebuilt from the topics, which are compacted, at any time; the reviews
themselves stay. From the repo root:

```sh
make reviews-rebuild
```

stops the service, drops `orders` and `products`, resets both consumer groups to the earliest
offset and starts it again. `make seed-reset` drops the whole `reviews` database instead, reviews
included, since every Order goes with it and the topics are emptied.

## Run it

```sh
docker compose up -d --build reviews-ratings   # from the repo root; listens on localhost:8097
```

That host port bypasses the [API gateway](../../platform/api-gateway/README.md), for development
only. Browsers reach the service through the gateway, at `/api/reviews-ratings/`, which lets the
three public reads through without a token.

Or run Mongo, Keycloak and the event backbone in compose (`docker compose up -d mongo keycloak
kafka-topics`) and start the service with `./gradlew :services:reviews-ratings:bootRun` on port
8080.

## Tests

- `ReviewsApiTest`, `RatingSummariesApiTest` and `ProjectionApiTest` publish Order and Product events as
  Order Management and Catalog would, each checked against its schema first, and drive the API over
  HTTP with `FakeKeycloak` tokens carrying names: which Orders count, posting once, editing and
  deleting only one's own, validation, paging, summaries one at a time and in a batch, stale and
  duplicate events, and events arriving out of order. Mongo, Kafka and the registry run in
  Testcontainers.
- `RatingSummaryTest` and `ReviewRulesTest` are pure domain tests: the average's rounding, what a
  review may say, display names, which Statuses count and which Variant was bought.
- `ArchitectureTest` runs the shared `HexagonalRules`.
