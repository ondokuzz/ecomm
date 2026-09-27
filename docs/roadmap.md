# Sprint Roadmap

Eight two-week sprints, five tracks running in parallel for a 4-5 person team. Sprint 1 is a deliberately thin **walking skeleton** through every layer — the fastest way to retire integration risk before any track goes deep.

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

> **Note (Sprint 1 implementation):** Kafka and Mongo are defined in Compose but sit behind the `full` profile (`docker compose --profile full up`), because nothing uses them until Sprint 3 and the default stack has to fit in about 8 GB of Docker memory. A plain `docker compose up` starts everything the definition of done needs, with the [Storefront](../frontend/storefront/README.md) on http://localhost:8080.

## Sprint 2 (Weeks 3–4) — Harden the Skeleton

| Track | Delivers |
|---|---|
| A | CI pipeline (build/test/lint/containerize); structured logging + correlation IDs; API gateway auth enforcement |
| B | Product variants + category attributes; admin catalog CRUD API |
| C | Pluggable payment-gateway interface; first promotion rules; checkout price calculation |
| D | Inventory reservation semantics (reserve on checkout, release on timeout) |
| E | Admin console shell (staff login, catalog editor); storefront polish + error states |

## Sprint 3 (Weeks 5–6) — Order CQRS/ES

| Track | Delivers |
|---|---|
| A | Kafka topics, partitions & consumer-group design; shared domain-event schema (JSON Schema + registry) |
| B | Search & Discovery v1 (faceted browse); Reviews & Ratings (post-purchase) |
| C | Campaign rules engine; checkout applies promotions |
| D | Order Management rebuilt as CQRS + event sourcing (Axon aggregate + Postgres event store); order-summary read projection; migrate Sprint-1 order data |
| E | Order history/detail pages on the new read model; admin order list/detail |

## Sprint 4 (Weeks 7–8) — Payment & Inventory CQRS/ES

| Track | Delivers |
|---|---|
| A | Idempotent, retrying event consumers; dead-letter topic handling; consumer-lag & error-rate dashboards |
| B | Recommendations v1 (co-occurrence from Order events) |
| C | Payment rebuilt as CQRS + event sourcing; idempotent gateway-webhook handling |
| D | Inventory rebuilt as CQRS + event sourcing; Redis-backed hot stock cache for checkout |
| E | Storefront/admin reflect real payment + stock states |

## Sprint 5 (Weeks 9–10) — Fulfillment + Returns Foundations

| Track | Delivers |
|---|---|
| A | Multi-warehouse config; secrets management hardening; Temporal server in Compose (Postgres persistence) |
| B | Warranty metadata on catalog items (length, manufacturer) |
| C | Shipping-cost estimate (abstracted provider); address validation |
| D | Fulfillment & Shipping — pick/pack/ship state machine, driven by a Temporal workflow Saga ([ADR 0009](./adr/0009-sagas-on-temporal.md)); Returns & Warranty v1 — request → approve/reject, IMEI/serial capture |
| E | Shipment tracking UI; admin fulfillment queue + RMA approval queue |

## Sprint 6 (Weeks 11–12) — Returns Completion

| Track | Delivers |
|---|---|
| A | Notification provider abstraction (email/SMS/push); message template system |
| B | Reviews moderation; recommendation quality pass |
| C | Promotion expiry/usage-limit edge cases; cart-abandonment detection |
| D | Returns & Warranty completion — received → inspected → refund/exchange; Temporal durable timer auto-closing a request after the Warranty Window lapses; wired to Payment refund + Inventory restock events |
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
| D | Event-replay + idempotency chaos testing on all event-sourced contexts |
| E | End-to-end UX polish; accessibility pass; admin completeness review |

**Definition of done**: deployed to a real environment, load-tested, soft-launch ready.
