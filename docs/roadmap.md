# Sprint Roadmap

Nine two-week sprints, five tracks running in parallel for a 4-5 person team. Sprint 1 is a deliberately thin **walking skeleton** through every layer — the fastest way to retire integration risk before any track goes deep.

## Tracks

| Track | Mandate | Owns |
|---|---|---|
| **A — Platform & Identity** | Infra, auth, and the shared plumbing every other track depends on | Identity & Access, Notifications, CI/CD, API Gateway, Observability |
| **B — Catalog & Discovery** | What customers find and trust before they buy | Catalog, Search & Discovery, Reviews & Ratings, Recommendations |
| **C — Cart, Checkout & Payment** | Turns browsing into a paid, priced order | Cart, Checkout & Pricing, Promotions, Payment |
| **D — Order, Fulfillment & Returns** | Owns an order's truth from placement to delivery — or its return | Order Management, Inventory, Fulfillment & Shipping, Returns & Warranty |
| **E — Frontend & AI Assistant** | Where customers touch the system, human or AI | Storefront, Admin Console, Support Assistant |

## Sprint 1 (Weeks 1–2) — Walking Skeleton

| Track | Delivers |
|---|---|
| A | Monorepo + Docker Compose (Postgres/Redis/Kafka/Couchbase/Mongo); hexagonal-architecture Spring Boot starter template; minimal JWT auth |
| B | Catalog CRUD + seed data (~20 SKUs, 3 categories); product list/detail read API |
| C | Redis-backed cart; checkout stub; mock payment adapter (always succeeds) |
| D | Order create/status (plain CRUD, no events yet); Inventory as a simple stock counter |
| E | React storefront — browse, cart, checkout, order confirmation |

**Definition of done**: `docker-compose up` → browse seeded products → add to cart → check out with a mock payment → see an order confirmation and status — entirely local.

> **Note (Sprint 1 implementation):** Kafka and Mongo are defined in Compose but sit behind the `full` profile (`docker compose --profile full up`), because nothing uses them until Sprint 3 and the default stack has to fit in about 8 GB of Docker memory. A plain `docker compose up` (or `make up`) starts everything the definition of done needs, with the [Storefront](../frontend/storefront/README.md) on http://localhost:8080. The root README's run section walked through it, and a Playwright smoke test proved it; [Run Sprint 3](../README.md#run-sprint-3) has since taken its place, Sprint 1's path included.

## Sprint 2 (Weeks 3–4) — Harden the Skeleton

| Track | Delivers |
|---|---|
| A | CI pipeline (build/test/lint/containerize); structured logging + correlation IDs; API gateway auth enforcement |
| B | Product variants + category attributes; admin catalog CRUD API |
| C | Pluggable payment-gateway interface; first promotion rules; checkout price calculation |
| D | Inventory reservation semantics (reserve on checkout, release on timeout) |
| E | Admin console shell (staff login, catalog editor); storefront polish + error states |

> **Note (Sprint 2 implementation):** The definition of done holds after `make up`: CI is green on `main`; a Customer picks a multi-Variant Product, opens checkout and sees their Checkout Session held for 15 minutes, applies `WELCOME10`, is declined once and then pays for a `PAID` Order with its Discount; Staff create a Product with two Variants and Stock in the Admin Console, and the Storefront shows it; an abandoned Checkout Session gives its Stock back once its Reservation expires; and one request can be followed across services in the logs by its Correlation ID. The root README's run section walked through it; [Run Sprint 3](../README.md#run-sprint-3) has since taken its place. The Storefront's and the Admin Console's Playwright suites prove the Customer's and the Staff's paths in CI; the expiry and the Correlation ID were shown by hand.
>
> What the build settled on:
>
> - **Stock is held, not taken, at checkout.** Starting checkout opens a Checkout Session in Redis that holds the Cart's Stock through an Inventory Reservation for 15 minutes; the Reservation lasts 2 minutes longer, so a payment started just before expiry can still commit it. An expired Reservation stops holding Stock at once, and a sweeper marks it released every 30 seconds ([Checkout ADR 0001](../services/checkout-pricing/docs/adr/0001-checkout-sessions-hold-stock-through-reservations.md)). Reservations keep no ledger of Stock movements yet; that is Sprint 3.
> - **Checkout calls the other contexts directly**, as in Sprint 1, now carrying the Correlation ID; the checkout Saga comes in Sprint 4. A declined or failed payment cancels the Order it placed but keeps the session, so the Customer can pay again.
> - **The first promotion rules are Coupons** in a new Promotions service: a percentage or a fixed amount off, with a validity window and an optional minimum. Campaign rules are Sprint 3's.
> - **The payment gateway is a port** with a mock adapter, driven by test tokens that approve, decline, decline for insufficient funds, or fail to answer.
> - **The API gateway** (port 8000) routes `/api/<service>/`, rejects missing or invalid tokens and hides internal endpoints ([ADR 0010](./adr/0010-api-gateway-authenticates-at-the-edge.md)); each service still authorizes every request itself. Rate limiting and circuit breakers wait for Sprint 7.
> - **Logs are JSON** (ECS) under the `docker` profile, each line carrying its Correlation ID. Metrics, tracing and dashboards wait for Sprint 4.
> - **Memory:** the gateway, Promotions and the Admin Console join the default stack, which still fits in about 8 GB of Docker memory with the Sprint 1 caps: `docker stats` measured about 3.4 GB in use on 2026-10-02, with the eight Spring services at 180–250 MB each, Keycloak near its 512 MB cap and Couchbase at 1 GB, so no cap needed lowering.
> - **Existing stacks** need `make seed-reset` once, and the Keycloak realm brought up to date for the Admin Console's sign-in ([A stack from Sprint 2](../README.md#a-stack-from-sprint-2) keeps these steps for a stack from Sprint 1).

## Sprint 3 (Weeks 5–6) — Events & Order History

No context is event-sourced: contexts keep append-only histories and ledgers where a requirement calls for them, and publish state-carrying, versioned integration events through transactional outboxes ([ADR 0002](./adr/0002-ledgers-and-outboxes-not-event-sourcing.md)).

| Track | Delivers |
|---|---|
| A | Kafka topics, partitions & consumer-group design; shared integration-event schemas (JSON Schema in the repo, registered in Apicurio); outbox publishing through Spring Modulith |
| B | Search & Discovery v1 (faceted browse); Reviews & Ratings (post-purchase) |
| C | Campaign rules engine; checkout applies promotions |
| D | Order Management: Order Status history, Order events through the outbox, order-summary read model, history backfilled for existing Orders; Inventory: ledger of Stock movements, Stock events through the outbox |
| E | Order history/detail pages on the new read model, with the real Order Status timeline; admin order list/detail |

> **Note (Sprint 3 implementation):** The definition of done holds after `make up`, on a fresh stack and on one carried over from Sprint 2. The root README's [Run Sprint 3](../README.md#run-sprint-3) walks through it. The Playwright suites prove the Customer's and the Staff's paths in CI. A Customer filters Audio by an attribute and to what is in stock, with the counts changing, and searches. They check out with Audio week applied by itself and `WELCOME10` on top, pay, see Placed then Paid with their times, review the Product and see its Rating summary on its card and page. A Customer who never bought a Product is told they can't review it. Staff find a Customer's Order and its history, see a changed Price in search within seconds, and create a Campaign that the next checkout applies, then find the Order it gave. Some checks were done by hand on 2026-10-06:
>
> - On a Sprint 2 stack, three Orders (one with `WELCOME10`, one declined) and an unpaid Checkout Session, upgraded with `make up` alone, came up with backfilled histories and the Coupon's Discount in the new list. The held laptop's ledger had an opening balance and its Reservation, and it balanced. Search held all 20 Products, and Reviews let the demo Customer review what they had paid for.
> - A Price changed with a Correlation ID showed in search 1.1 seconds later. The ID appeared at the gateway, then in Search's and Reviews' consumers as they applied the `catalog.product` event.
> - A payment made through the Storefront with a Correlation ID was followed from the Storefront's nginx, through the gateway, Checkout and the contexts it calls, into Reviews' consumer applying the Order's events and Search's applying the Stock event.
> - A schema change that isn't backward compatible failing CI was proven with the event backbone (#41).
>
> What the build settled on:
>
> - **Events are snapshots on compacted topics.** Each of `order-management.order`, `inventory.stock`, `catalog.product` and `catalog.category` carries one event type: its aggregate's state, version and why it was published. Topics have 6 partitions locally, and the broker never creates one itself. Their JSON Schemas live in [`platform/event-schemas`](../platform/event-schemas/README.md) and are registered in Apicurio 3.3 with BACKWARD compatibility. Producers validate against them, and consumers read tolerantly.
> - **Publishing goes through an outbox.** The Postgres services use Spring Modulith 2.1's event publication registry, which ran under Boot 4's Jackson 3 beside Apicurio's Jackson 2 serializer, as the spike had to prove. Catalog writes its own outbox document in the same Couchbase transaction, and a relay sends it. Consumers keep the newest version, and they retry an event that fails, then log it and skip it. Dead-letter topics are Sprint 4's.
> - **Histories and ledgers, not event sourcing.** Order Management appends an Order Status history entry with every change, and Inventory a Stock movement with every change to On-hand or to what Reservations hold. Both were backfilled by migrations, and each context publishes everything once on its first start, so projections start complete. The Staff Order list is a query over Order Management's own tables.
> - **Search and Reviews are projections in Mongo.** Search computes disjunctive Facets with Mongo's text index and aggregations ([Search ADR 0001](../services/search-discovery/docs/adr/0001-search-on-mongodb.md)); Elasticsearch is Sprint 9's. Reviews checks a purchase against its own projection of Orders and Products, only when a review is posted ([Reviews ADR 0001](../services/reviews-ratings/docs/adr/0001-reviews-on-mongodb.md)). `make search-rebuild` and `make reviews-rebuild` replay their topics.
> - **Campaigns and Coupons are worked out together.** Promotions returns every Discount a Checkout Session's lines are due, Campaigns by priority and then the Coupon. The session and its Order keep them as a list. Usage limits are Sprint 6's.
> - **Checkout still calls the other contexts directly.** The Saga, idempotency keys and the Orchestration identity are Sprint 4's.
> - **The smoke tests make their own Customers** through Keycloak's admin API, so a review or a Cart is the test's alone. Within the Storefront's suite, every test that checks out sits in the one serial smoke test, since its first test compares Stock before and after; the Admin Console's suite checks out too, so the two run one after the other, as CI runs them.
> - **Memory:** Kafka, Mongo, Apicurio, Search & Discovery and Reviews & Ratings joined the default stack. On 2026-10-06, after both Playwright suites had run, `docker stats` measured about 4.8 GB in use. The ten Spring services were at 200–340 MB each, Catalog nearest its 384 MB cap at 342 MB. Couchbase was at 0.9 GB, Kafka at 350 MB and Mongo at 110 MB. Keycloak's cap went up from 512 MB to 768 MB, with its heap share cut from 60% to 40% so the heap stays about 300 MB. At 512 MB it sat at the limit from start, and after a day of e2e runs it was killed for memory; under the suites it now reaches about 590 MB. The capped containers add up to 6 GB, so the stack still fits in about 8 GB.
> - **Existing stacks** need only `make up` ([A stack from Sprint 2](../README.md#a-stack-from-sprint-2)).

## Sprint 4 (Weeks 7–8) — Checkout Saga & Payment Ledger

| Track | Delivers |
|---|---|
| A | Temporal server in Compose (Postgres persistence); Orchestration service and its client-credentials identity; idempotency keys on command APIs; retrying event consumers; dead-letter topic handling; consumer-lag & error-rate dashboards |
| B | Recommendations v1 (co-occurrence from Order events) |
| C | Checkout Saga on Temporal ([ADR 0009](./adr/0009-sagas-on-temporal.md)), closing the authorized-but-cancelled gap with a void; Payment rebuilt on a ledger of Payment transactions, with void; idempotent gateway-webhook handling |
| D | Saga-facing commands on Order Management and Inventory; Redis-backed hot stock cache for checkout |
| E | Storefront/admin reflect real payment + stock states |

> **Note (Sprint 4 implementation, in progress):** This note grows with the sprint's tickets.
>
> - **Temporal and Orchestration (#55).** Temporal's server 1.32 joins the default stack, persisting to the shared Postgres in its own `temporal` and `temporal_visibility` databases. An `admin-tools` step sets up or migrates their schema, and another creates the `default` namespace with a 7-day retention. Its UI is on port 8233. Orchestration is its worker on the `checkout` task queue, with a placeholder workflow for now. A spike proved the Temporal Java SDK 1.40 and its Spring Boot starter under Boot 4.1: workflow payloads go through the SDK's Jackson 2 beside Boot's Jackson 3, as Apicurio's serializer does. Each of Temporal's four services keeps its own pools on Postgres, which at their defaults used up the 100 connections it allowed. Their pools were cut, and Postgres now allows 200; idle, the stack holds about 80. Apicurio now restarts with Postgres, since recreating Postgres for that setting cut its pooled connections just as `schema-registry-init` used them.
> - **Idempotency keys (#56).** A Postgres service declares a command idempotent with `@IdempotentCommand`, and `service-commons` does the rest: the command runs in a transaction its use case joins, with the key claimed first and the 2xx response recorded before the commit, so a repeat is replayed and a concurrent one waits for the first. Keys are scoped to the token's `client_id`, or else its `sub`, kept for 7 days and pruned daily. Each service adds the `idempotency_key` table in its own migration, as the [service template](../platform/service-template/README.md#idempotent-commands) shows. The key Orchestration is to send gained the run ID ([ADR 0009](./adr/0009-sagas-on-temporal.md)).
> - **Metrics and dashboards (#57).** `service-commons` brings Micrometer's Prometheus registry, so every Spring service, the gateway included, serves `/actuator/prometheus`, open without a token and tagged with the service's name; the gateway routes no service's `/actuator/**`. Beside Spring's HTTP metrics, with histograms for p95, every service exports Kafka's consumer client metrics, a counter of retried events per group and topic, and, with an outbox, a gauge of its incomplete publications per status. Kafka's client metrics name only the client, so `service-commons` binds them with the consumer group as a tag: added by a `MeterFilter`, the tag broke Micrometer's Kafka binder, which compares tags to tell its meters apart and then bound only the first partition's lag. Kafka also reports per-topic metrics a second time under the topic's legacy name, dots turned to underscores; those copies are dropped. Prometheus 3.15 (2-day retention) and Grafana 13.2 join the default stack on ports 9090 and 3000, Grafana open for viewing, with its data source and the **Services** and **Event consumers** dashboards provisioned from `infra/docker/grafana`.
> - **Memory (#57):** On 2026-10-08, after the Storefront's smoke suite had checked out, `docker stats` measured about 5.4 GB in use, up from 5.0 GB. Prometheus took 67 MB of its 256 MB. Grafana took 229 MB of its 256 MB, of which 107 MB was reclaimable file cache; it runs its schema migrations afresh on every start, since it keeps no volume. The capped containers now add up to 7.3 GB, so the stack still fits in about 8 GB. No cap was lowered, and Prometheus and Grafana stay in the default stack, not behind a Compose profile.
> - **Realms are brought up to date.** The `keycloak-realm` step adds the roles and clients a running realm lacks from the realm file, through Keycloak's admin API, and changes nothing that exists. A service account it creates holds only the file's roles, as an imported one does, not the realm's default `CUSTOMER`. On 2026-10-07 it gave this machine's Sprint 3 realm the `orchestration` client and the `ORCHESTRATION` role, and a second run changed nothing.
> - **Memory:** On 2026-10-07, `docker stats` measured about 4.9 GB in use before Temporal and Orchestration, and about 5.0 GB after. Temporal took 95 MB of its 384 MB, its UI 34 MB of 64 MB, and Orchestration 300 MB of 384 MB. Couchbase, just restarted, used about 240 MB less than before, which hides most of the increase. The long-running capped containers add up to about 6.8 GB, so with Couchbase, Postgres and Redis uncapped the stack still fits in about 8 GB, and no cap was lowered.

## Sprint 5 (Weeks 9–10) — Fulfillment + Returns Foundations

| Track | Delivers |
|---|---|
| A | Multi-warehouse config; secrets management hardening |
| B | Warranty metadata on catalog items (length, manufacturer) |
| C | Shipping-cost estimate (abstracted provider); address validation |
| D | Fulfillment & Shipping — pick/pack/ship state machine, driven by a Saga in Orchestration ([ADR 0009](./adr/0009-sagas-on-temporal.md)); Returns & Warranty v1 — request → approve/reject, IMEI/serial capture |
| E | Shipment tracking UI; admin fulfillment queue + RMA approval queue |

## Sprint 6 (Weeks 11–12) — Returns Completion

| Track | Delivers |
|---|---|
| A | Notification provider abstraction (email/SMS/push); message template system |
| B | Reviews moderation; recommendation quality pass |
| C | Promotion expiry/usage-limit edge cases; cart-abandonment detection |
| D | Returns & Warranty completion — received → inspected → refund/exchange; the returns Saga's durable timer auto-closing a request after the Warranty Window lapses; refund and restock through Payment's and Inventory's commands |
| E | Return-request UI; admin RMA lifecycle UI; notifications wired to order/payment/fulfillment/return events |

## Sprint 7 (Weeks 13–14) — AI Assistant

| Track | Delivers |
|---|---|
| A | Rate limiting + circuit breaker around the AI provider call; secrets for the AI API key |
| B | Read-optimized catalog/search endpoints for the assistant to query |
| C | Payment event-history query endpoint (for "why was I charged twice") |
| D | Order/Returns read APIs exposed for assistant grounding |
| E | Support Assistant service (LLM-backed, stays in Java per ADR 0004); storefront chat widget |

## Sprint 8 (Weeks 15–16) — Launch Readiness

| Track | Delivers |
|---|---|
| A | Load/performance testing; move to AWS (ECS/EKS + RDS + ElastiCache); Kubernetes manifests |
| B | SEO pass (SSR, Core Web Vitals); catalog data-quality review |
| C | Checkout conversion hardening; abandoned-cart recovery |
| D | Outbox, relay and Saga chaos testing: duplicate and reordered events, projection rebuilds from topics, retried and compensated Saga steps |
| E | End-to-end UX polish; accessibility pass; admin completeness review |

**Definition of done**: deployed to a real environment, load-tested, soft-launch ready.

## Sprint 9 (Weeks 17–18) — Search Engine & Event Contracts

| Track | Delivers |
|---|---|
| A | Contract tests for every integration event: each producer's tests check the events it publishes against its topic's schema in `platform/event-schemas`, and each consumer's tests check that what it reads is what the schema promises, so a change that breaks a consumer fails in CI before it reaches a topic |
| B | Search & Discovery on Elasticsearch: the projection indexes Products into it from the same topics, with search, filters and disjunctive Facets moved onto its query and aggregations, rebuilt from the topics; revises [Search ADR 0001](../services/search-discovery/docs/adr/0001-search-on-mongodb.md) |
| C | — |
| D | — |
| E | A fancier Storefront and Admin Console: a richer visual design, and a new logo and brand colour in place of the purple (`--color-primary` and its shades in `index.css`, the logo in `Layout.tsx`, and the Keycloak theme and Admin Console brand generated from them). MCP and RAG for the Support Assistant: the contexts' read APIs it grounds on exposed as MCP tools, and retrieval over Catalog's Product content and the store's policies |

**Definition of done**: Search runs on Elasticsearch with the same results and Facets as v1, and every integration event's producers and consumers are held to its schema in CI.
