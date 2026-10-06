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

> **Note (Sprint 1 implementation):** Kafka and Mongo are defined in Compose but sit behind the `full` profile (`docker compose --profile full up`), because nothing uses them until Sprint 3 and the default stack has to fit in about 8 GB of Docker memory. A plain `docker compose up` (or `make up`) starts everything the definition of done needs, with the [Storefront](../frontend/storefront/README.md) on http://localhost:8080. The root README's run section walked through it, and a Playwright smoke test proved it; [Run Sprint 2](../README.md#run-sprint-2) has since taken its place, Sprint 1's path included.

## Sprint 2 (Weeks 3–4) — Harden the Skeleton

| Track | Delivers |
|---|---|
| A | CI pipeline (build/test/lint/containerize); structured logging + correlation IDs; API gateway auth enforcement |
| B | Product variants + category attributes; admin catalog CRUD API |
| C | Pluggable payment-gateway interface; first promotion rules; checkout price calculation |
| D | Inventory reservation semantics (reserve on checkout, release on timeout) |
| E | Admin console shell (staff login, catalog editor); storefront polish + error states |

> **Note (Sprint 2 implementation):** The definition of done holds after `make up`: CI is green on `main`; a Customer picks a multi-Variant Product, opens checkout and sees their Checkout Session held for 15 minutes, applies `WELCOME10`, is declined once and then pays for a `PAID` Order with its Discount; Staff create a Product with two Variants and Stock in the Admin Console, and the Storefront shows it; an abandoned Checkout Session gives its Stock back once its Reservation expires; and one request can be followed across services in the logs by its Correlation ID. The root README's [Run Sprint 2](../README.md#run-sprint-2) walks through it. The Storefront's and the Admin Console's Playwright suites prove the Customer's and the Staff's paths in CI; the expiry and the Correlation ID were shown by hand.
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
> - **Existing stacks** need `make seed-reset` once, and the Keycloak realm brought up to date for the Admin Console's sign-in ([A stack from Sprint 1](../README.md#a-stack-from-sprint-1)).

## Sprint 3 (Weeks 5–6) — Events & Order History

No context is event-sourced: contexts keep append-only histories and ledgers where a requirement calls for them, and publish state-carrying, versioned integration events through transactional outboxes ([ADR 0002](./adr/0002-ledgers-and-outboxes-not-event-sourcing.md)).

| Track | Delivers |
|---|---|
| A | Kafka topics, partitions & consumer-group design; shared integration-event schemas (JSON Schema in the repo, registered in Apicurio); outbox publishing through Spring Modulith |
| B | Search & Discovery v1 (faceted browse); Reviews & Ratings (post-purchase) |
| C | Campaign rules engine; checkout applies promotions |
| D | Order Management: Order Status history, Order events through the outbox, order-summary read model, history backfilled for existing Orders; Inventory: ledger of Stock movements, Stock events through the outbox |
| E | Order history/detail pages on the new read model, with the real Order Status timeline; admin order list/detail |

## Sprint 4 (Weeks 7–8) — Checkout Saga & Payment Ledger

| Track | Delivers |
|---|---|
| A | Temporal server in Compose (Postgres persistence); Orchestration service and its client-credentials identity; idempotency keys on command APIs; retrying event consumers; dead-letter topic handling; consumer-lag & error-rate dashboards |
| B | Recommendations v1 (co-occurrence from Order events) |
| C | Checkout Saga on Temporal ([ADR 0009](./adr/0009-sagas-on-temporal.md)), closing the authorized-but-cancelled gap with a void; Payment rebuilt on a ledger of Payment transactions, with void; idempotent gateway-webhook handling |
| D | Saga-facing commands on Order Management and Inventory; Redis-backed hot stock cache for checkout |
| E | Storefront/admin reflect real payment + stock states |

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
