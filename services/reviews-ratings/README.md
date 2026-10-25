# Reviews & Ratings

Customers rate and review what they bought, and report each other's reviews; Staff moderate them.
Reviews & Ratings keeps the reviews in its own MongoDB
database, `reviews` ([ADR 0001](./docs/adr/0001-reviews-on-mongodb.md)), beside a projection of
Order Management's and Catalog's integration events that tells it who bought which Product. It
publishes no events. Built from [`platform/service-template`](../../platform/service-template/README.md),
so its layout, security and testing conventions apply here.

## API

| Endpoint | Who | |
|---|---|---|
| `GET /products/{sku}/reviews` | anyone | A page of the Product's published reviews, newest first |
| `GET /products/{sku}/rating-summary` | anyone | The Product's rating summary, of published reviews |
| `GET /rating-summaries?sku=…&sku=…` | anyone | Up to 50 Products' summaries at once |
| `GET /products/{sku}/eligibility` | `CUSTOMER` | Whether I may review the Product, and why not, with my review whatever its status |
| `POST /products/{sku}/reviews` | `CUSTOMER` | Post my review: 201, 403 `notPurchased`, 409 `alreadyReviewed` |
| `PUT /reviews/{id}` | `CUSTOMER` | Edit my review; another Customer's is a 404, a removed one a 409 `reviewRemoved` |
| `DELETE /reviews/{id}` | `CUSTOMER` | Delete my review, whatever its status: 204; another Customer's is a 404 |
| `POST /reviews/{id}/reports` | `CUSTOMER` | Report another Customer's published review: 201, or 200 with my first report; my own is a 403 `ownReview` |
| `GET /moderation/reviews` | `STAFF` | A page of the moderation queue, oldest first |
| `GET /moderation/reviews/{id}` | `STAFF` | Any review, whatever its status, with its reports and moderation history |
| `POST /reviews/{id}/publish` | `STAFF` | Publish a held review: 409 `notHeld` for any other |
| `POST /reviews/{id}/dismiss-reports` | `STAFF` | Resolve a published review's open reports: 409 `noOpenReports` without any |
| `POST /reviews/{id}/remove` | `STAFF` | Remove a review, with a required `reason`: 400 without one, 409 `alreadyRemoved` |

[`http/reviews-ratings.http`](./http/reviews-ratings.http) exercises each against the compose stack.

### A review

```json
{"id": "6c1f…", "sku": "PHN-PIXEL-9", "author": "Demo C.", "variantId": "PHN-PIXEL-9-OBSIDIAN-256",
 "rating": 4, "title": "Great camera", "body": "Sharp photos, and the battery lasts a day.",
 "createdAt": "2026-10-06T14:02:11.204Z", "editedAt": null, "status": "PUBLISHED", "removalReason": null}
```

- `rating` is from 1 to 5. `title` is optional, up to 120 characters, and `null` when blank. `body`
  is required, up to 2,000 characters. Both are trimmed. Anything else is a 400 problem detail.
- `author` is the token's `given_name` and the first letter of its `family_name`, captured when the
  review is posted: `Demo C.`. A missing part is left out, and a token with neither is `A Customer`.
  A review never shows the Customer's ID.
- `variantId` is the Variant the Customer bought, from their latest Order that counts with one of
  the Product's Variants.
- `editedAt` is `null` until the review is first edited. Editing keeps the Variant and the author.
- `status` is `PUBLISHED`, `HELD` or `REMOVED` ([Moderation](#moderation)). Only a published review
  is listed or counted in a Rating summary, so the public reads only ever show `PUBLISHED`. Its
  author sees theirs whatever its status in `GET /products/{sku}/eligibility`, and a removed one's
  `removalReason`, which is `null` otherwise. A review from before moderation is `PUBLISHED`.

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
another if they still have an Order that counts. A held or removed review is still their one review
of the Product, so a removed one has to be deleted before they write another.

### A rating summary

```json
{"sku": "PHN-PIXEL-9", "count": 3, "average": 4.7, "perStar": {"1": 0, "2": 0, "3": 0, "4": 1, "5": 2}}
```

`average` is to one decimal, a half rounded up, and `null` without reviews. `GET /rating-summaries` takes
`sku` repeated, 1 to 50 distinct SKUs or it is a 400, and answers a list of summaries in the order
asked, a SKU asked twice once. A SKU nobody has reviewed, or one Reviews has never heard of, has a
count of 0.

## Moderation

### The screen

Posting or editing a review runs its title and body through the screen, `ecomm.reviews.screen` in
[`application.yml`](./src/main/resources/application.yml): a list of words, phrases and patterns,
each a regular expression. One matches as a whole word or phrase, never inside a longer word, and
ignoring case and accents on both sides: `scam` holds "Total SCÁM!" but not "scampi", and
`fr[e3]{2} (money|gifts?)` holds "Fr33 money". A match holds the review: `HELD`, not listed or
counted, until Staff publish or remove it. Otherwise it is published, so an edit that clears a held
review's text publishes it, and one that trips the screen holds a published one. An entry that isn't
a regular expression fails the start. Editing a `REMOVED` review is a 409 `reviewRemoved`.

### Reports

`POST /reviews/{id}/reports` takes `{"reason", "note"}`: a reason of `SPAM`, `OFFENSIVE`,
`OFF_TOPIC` or `OTHER`, and an optional note of up to 500 characters, trimmed. Any other reason, or
a longer note, is a 400. It answers `{"reason", "note", "reportedAt"}`. Any Customer may report
another's published review, without having bought the Product. A Customer reports a review once:
reporting it again answers 200 with their first report, whatever the second says. Their own review
is a 403 `ownReview`, and one that isn't published, or doesn't exist, a 404. A report never hides a
review: it puts a published review in the queue until Staff resolve its reports.

### The queue and Staff's actions

`GET /moderation/reviews` lists held reviews and published ones with open reports, oldest review
first, a page at a time: `page` from 0, `size` from 1 to 50, 20 by default. Each item is a review as
Staff see it, from either endpoint:

```json
{"id": "6c1f…", "sku": "PHN-PIXEL-9", "customerId": "8d0e…", "author": "Demo C.",
 "variantId": "PHN-PIXEL-9-OBSIDIAN-256", "rating": 1, "title": null, "body": "The seller is a crook.",
 "createdAt": "2026-10-25T20:02:11.204Z", "editedAt": null, "status": "PUBLISHED",
 "openReports": {"SPAM": 0, "OFFENSIVE": 2, "OFF_TOPIC": 0, "OTHER": 0},
 "reports": [{"customerId": "41aa…", "reason": "OFFENSIVE", "note": null,
              "reportedAt": "2026-10-25T20:10:00.000Z", "resolved": false}, …],
 "history": [{"action": "REMOVED", "staffId": "4b6f…", "at": "2026-10-25T20:30:00.000Z",
              "reason": "Insults the seller."}]}
```

`openReports` counts the open reports of each reason. `reports` holds every one, open or resolved,
and `history` every moderation action, oldest first: its `action` (`PUBLISHED`,
`REPORTS_DISMISSED` or `REMOVED`), the Staff member's ID from the token's `sub`, when, and a
removal's reason, `null` for the others. Each action resolves every open report, so the review
leaves the queue:

- **publish** a held review: it is shown and counted;
- **dismiss-reports** of a published review with open reports: it stays up;
- **remove** a held or published review with `{"reason"}`, 1 to 1,000 characters, trimmed: it is
  neither shown nor counted, and its author sees the reason.

An action the review's status rules out, perhaps because another Staff member's came first, is a
409 with a `reason`. A later report puts a review whose reports were dismissed back in the queue.

## The projection

Reviews consumes two topics, each in its own consumer group, and keeps the newest version of every
aggregate it is sent; a duplicate or an older event is ignored. An event it can't apply goes to its
group's dead-letter topic, such as `reviews-ratings.orders.dlt`, after its retries, or at once if
it can't be read, and `make dlt-redrive GROUP=<group>` applies it once the cause is fixed
([service template](../../platform/service-template/README.md#consuming)).

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

`reviews` holds one document per review, keyed by its ID, with its `status`, its `reports` and its
moderation `history` embedded, and three indexes: `oneReviewPerCustomer`, unique on the SKU and the
Customer, `newestFirst` on the SKU, `createdAt` and the ID, and `moderationQueue` on `createdAt`
and the ID, partial on `awaitingModeration`, which each write keeps true for a review the queue
lists. Summaries are counted with one aggregation over the SKUs asked, grouped by SKU and rating,
of published reviews only. A review from before moderation has no `status`, which reads as
`PUBLISHED` in the reads and in the summaries' aggregation, so nothing is migrated.

Every change to a review after it is posted, an edit, a report or a moderation action, replaces its
document only if its `version` is the one read, and applies the change again to the newer review if
another was kept first. Reports made at once are all kept, and an action that another made moot is
refused with its 409. `orders` is indexed by Customer.

### Rebuilding

The projection can be rebuilt from the topics, which are compacted, at any time; the reviews
themselves stay. From the repo root:

```sh
make reviews-rebuild
```

stops the service, resets both consumer groups to the earliest offset, apart from their redrive
topics, which are moved to their end, drops `orders` and `products`, and starts it again
([`replay-groups.sh`](../../infra/docker/replay-groups.sh) fails the command, before anything is
dropped, if Kafka refuses a reset). `make seed-reset` drops the whole `reviews` database instead, reviews
included, since every Order goes with it and the topics are emptied.

## Run it

```sh
docker compose up -d --build reviews-ratings   # from the repo root; listens on localhost:8097
```

That host port bypasses the [API gateway](../../platform/api-gateway/README.md), for development
only. Browsers reach the service through the gateway, at `/api/reviews-ratings/`, which lets the
three public reads through without a token. The Admin Console's Review moderation page calls the
Staff endpoints there too.

Or run Mongo, Keycloak and the event backbone in compose (`docker compose up -d mongo keycloak
kafka-topics`) and start the service with `./gradlew :services:reviews-ratings:bootRun` on port
8080.

## Tests

- `ModerationApiTest` drives the screen, reports and Staff's moderation through the same API: held
  reviews neither shown nor counted, editing in and out of the screen, reports once per Customer
  and never by the author, twelve reports at once all kept, the queue's order and counts, each
  action with its history and refusals, a removed review's reason for its author, and a review
  written as Sprint 5 stored it read as published.
- `ReviewsApiTest`, `RatingSummariesApiTest` and `ProjectionApiTest` publish Order and Product events as
  Order Management and Catalog would, each checked against its schema first, and drive the API over
  HTTP with `FakeKeycloak` tokens carrying names: which Orders count, posting once, editing and
  deleting only one's own, validation, paging, summaries one at a time and in a batch, stale and
  duplicate events, and events arriving out of order. Mongo, Kafka and the registry run in
  Testcontainers.
- `RatingSummaryTest`, `ReviewRulesTest` and `ReviewScreenTest` are pure domain tests: the
  average's rounding, what a review may say, display names, which Statuses count, which Variant was
  bought, and the screen's matching (case, accents, word boundaries and patterns).
- `ArchitectureTest` runs the shared `HexagonalRules`.
