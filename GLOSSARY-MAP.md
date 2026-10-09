# Context Map

## Contexts

| Context | Lives at | Owns |
|---|---|---|
| Identity & Access | Keycloak realm `ecomm` (config in `services/identity-access`) | Customer and Staff authentication, roles and permissions |
| Catalog | `services/catalog` | Products, Variants, categories, specs, pricing |
| Search & Discovery | `services/search-discovery` | Search and faceted filtering of the Catalog: a read projection over Catalog's and Inventory's events, in MongoDB |
| Reviews & Ratings | `services/reviews-ratings` | Reviews and ratings by Customers who paid for what they review, in MongoDB, checked against a projection of Order Management's and Catalog's events |
| Recommendations | `services/recommendations` | "Customers also bought," sourced from Order + Catalog events |
| Cart | `services/cart` | The ephemeral per-Customer Cart |
| Checkout & Pricing | `services/checkout-pricing` | Checkout Sessions: pricing, tax and holding a Cart's Stock; paying one starts the checkout Saga |
| Promotions | `services/promotions` | Coupons and Campaigns |
| Payment | `services/payment` | Gateway integration, authorization/capture/refund, kept as a ledger of Payment transactions |
| Order Management | `services/order-management` | Order lifecycle from placement to delivery, with its Order Status history |
| Inventory | `services/inventory` | Stock levels, Reservations, replenishment, kept as a ledger of Stock movements |
| Fulfillment & Shipping | `services/fulfillment-shipping` | Pick/pack/ship, carrier hand-off |
| Returns & Warranty | `services/returns-warranty` | RMA workflow, Warranty Window validation, with each RMA's decision history |
| Notifications | `services/notifications` | Email/SMS/push dispatch, triggered by domain events |
| AI Support Assistant | `services/ai-support-assistant` | The Support Assistant, grounded in real order/payment/returns data |
| Orchestration | `services/orchestration` | The Sagas — checkout, fulfillment and returns — as workflows on the Temporal server in Compose, which keeps their state in its own Postgres databases; no domain data and no public API of its own |

Not bounded contexts — UI layers over the above:

| App | Lives at |
|---|---|
| Storefront | `frontend/storefront` |
| Admin Console | `frontend/admin-console` |

Browsers reach the services only through the API gateway ([`platform/api-gateway`](./platform/api-gateway/README.md)), which the Storefront's and the Admin Console's nginx send everything under `/api/` to. The gateway routes `/api/<service>/` to Catalog, Inventory, Cart, Checkout & Pricing, Order Management, Promotions, Search & Discovery and Reviews & Ratings, rejects a missing or invalid token at the edge, and never routes internal endpoints; each service still authorizes every request itself ([ADR 0010](./docs/adr/0010-api-gateway-authenticates-at-the-edge.md)).

A request keeps one Correlation ID across every context. It gets one where it enters, unless the caller sent a well-formed one: in the Storefront's or the Admin Console's nginx for a browser, or at the gateway for any other caller. Every service logs it and returns it in problem details, and passes it on every call it makes to another context. Paying hands the Pay request's to the checkout Saga, whose every step carries it, so one request can be followed through the logs, Temporal included.

## Relationships

> **Since Sprint 4 (#60):** Checkout starts a Checkout Session itself, reserving its Stock through Inventory ([Checkout ADR 0001](./services/checkout-pricing/docs/adr/0001-checkout-sessions-hold-stock-through-reservations.md)), and paying it starts the checkout Saga in Orchestration, which calls the other contexts. In Sprints 1–3 Checkout called Order Management and Payment, and committed the Reservation, itself.

- **Cart → Checkout & Pricing**: Checkout reads the Cart's contents to start a Checkout Session.
- **Catalog → Checkout & Pricing**: a Checkout Session prices every line from Catalog's Price when it starts, never from the Cart, and keeps that Price for its lifetime, with the line's Product SKU and Category.
- **Catalog → Search & Discovery**: Search projects Catalog's `catalog.product` and `catalog.category` events, so a Product is searchable, and a Price or a removal shows, a moment after Catalog publishes it. Search keeps the newest version of each and never calls Catalog. The Storefront's listing is Search's ([Search ADR 0001](./services/search-discovery/docs/adr/0001-search-on-mongodb.md)).
- **Inventory → Search & Discovery**: Search projects Inventory's `inventory.stock` events to tell which Products are in Stock; a Variant with no Stock event yet counts as out of Stock.
- **Order Management → Reviews & Ratings**: Reviews projects Order Management's `order-management.order` events to know which Customer bought which Variant, and whether that Order counts: `Paid` or later, and not `Cancelled` or `Returned`. It checks this only when a review is posted, so a review stays if its Order is later cancelled. It keeps the newest version of each Order and never calls Order Management.
- **Catalog → Reviews & Ratings**: Reviews projects Catalog's `catalog.product` events to know each Variant's Product, since an Order names Variants and a review is of a Product. The Storefront's Product cards and pages show Reviews' rating summaries beside Search's and Catalog's data ([Reviews ADR 0001](./services/reviews-ratings/docs/adr/0001-reviews-on-mongodb.md)).
- **Catalog → Promotions**: a Campaign's Categories must be Catalog's, and its amounts in currencies Catalog prices in. Promotions reads Catalog's public Categories and currencies when Staff save a Campaign.
- **Checkout & Pricing → Promotions**: starting a Checkout Session, and applying or removing its Coupon, asks Promotions for every Discount the session's lines are due: the running Campaigns', then the Coupon's, or why the Coupon doesn't apply. Each line goes with its Product's SKU and Category, which decide the Campaigns it gets. Only Checkout may ask, with its own identity, and the gateway never routes it.
- **Checkout & Pricing → Inventory**: starting a Checkout Session reserves the Cart's Stock, and replacing one releases its Reservation, with Checkout's own identity.
- **Checkout & Pricing → Orchestration**: paying a Checkout Session starts the checkout Saga through the Temporal client, with the session's ID as the workflow's and the session as its input, and reads how it ended. Checkout calls no other context to pay.
- **Orchestration → Order Management, Payment, Inventory, Cart, Checkout & Pricing**: the checkout Saga places the Order, authorizes the Payment, commits the Reservation, marks the Order paid, clears the Cart and ends the session. It compensates a failure by cancelling the Order and voiding an authorized Payment, so a Payment is never left authorized for a cancelled Order. Each step calls the context's command API, with an idempotency key where repeating the command would act twice ([ADR 0009](./docs/adr/0009-sagas-on-temporal.md)); each context owns its own lifecycle.
- **Orchestration → Fulfillment & Shipping, Payment, Order Management**: the fulfillment Saga drives pick/pack/ship once an Order is paid, captures the Payment and moves the Order along.
- **Orchestration → Returns & Warranty, Payment, Inventory**: the returns Saga drives an RMA to its resolution, refunding through Payment and restocking through Inventory, and closes it when its Warranty Window lapses.
- **Payment → Notifications, AI Support Assistant**: Payment publishes each change to a Payment, with its Payment transactions, as a `payment.payment` event. Nothing consumes them yet; Notifications (Sprint 6) and the Support Assistant (Sprint 7) will.
- **Order Management → Notifications**: order-lifecycle events trigger customer notifications.
- **Order Management, Payment, Returns & Warranty → AI Support Assistant**: the assistant queries each context's read APIs to ground its answers in real Customer data.
- **Identity & Access → all contexts**: every service trusts Keycloak-issued JWTs. A Customer's token names the Customer. Checkout and Orchestration each call other contexts with their own client-credentials identity, the `checkout` and `orchestration` clients holding the `CHECKOUT` and `ORCHESTRATION` roles, and each command names the one caller allowed to make it: Checkout reserves and releases Stock and evaluates Discounts, and Orchestration makes every command the checkout Saga calls ([ADR 0002](./services/identity-access/docs/adr/0002-service-identity-by-client-credentials.md)).
- **All contexts ↔ Kafka**: the shared backbone for integration events, published from each context's outbox and versioned so consumers keep the newest ([ADR 0002](./docs/adr/0002-ledgers-and-outboxes-not-event-sourcing.md), [ADR 0006](./docs/adr/0006-kafka-as-single-event-backbone.md)). Commands never travel over it.

## Shared vocabulary

Term definitions currently live in one shared [`GLOSSARY.md`](./GLOSSARY.md) at the root rather than per-context files — the vocabulary hasn't diverged per context yet. A context earns its own `GLOSSARY.md` (and this map gets updated to point to it) once its terms need a local definition that differs from the shared one.

## ADRs

System-wide decisions live in [`docs/adr/`](./docs/adr/). Context-specific decisions live inside the context they belong to (e.g. [`services/catalog/docs/adr/`](./services/catalog/docs/adr/), [`services/checkout-pricing/docs/adr/`](./services/checkout-pricing/docs/adr/)).
