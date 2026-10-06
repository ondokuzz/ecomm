# Ecomm Platform

A from-scratch e-commerce platform, architected as fifteen bounded contexts across five parallel team tracks, planned over nine two-week sprints for a small (4-5 person) engineering team. Sprint 2 has hardened Sprint 1's walking skeleton, and it all runs locally: browse Products and pick their Variants, keep a Cart, check out with a Checkout Session that reserves its Stock, a Coupon and a mock payment that can decline, and follow the Order. Staff manage the Catalog in an Admin Console.

## Run Sprint 2

You need Docker with about 8 GB of memory, `make`, and Node 24 for the smoke tests.

```sh
make up           # build and start the stack; returns once every service is healthy
```

The first build takes several minutes. Then open the Storefront on http://localhost:8080, where you can:

1. search the 20 seeded Products, and narrow them by category, by attribute (storage, brand,
   …), by Price and to what is in stock, with each choice's count beside it; the listing's URL keeps
   the view. The Google Pixel 9, the Apple iPhone 16 and the Apple
   MacBook Air 13 (M3) come in several Variants: pick one by its color and storage, each with its
   own Price and Stock. One iPhone Variant starts sold out;
2. sign in as `demo@ecomm.local` / `demo`, or register a new Customer on Keycloak's page;
3. add Products to the Cart and go to checkout, which holds them and their Prices for 15 minutes
   and shows the time left. Enter the Coupon `WELCOME10` for 10% off, then pick a test card and press
   **Pay**. Paying again with another card works while the Checkout Session lasts;
4. see the Order confirmation with its Order Status, **Paid**, and its discount, and find the Order
   under **My Orders**;
5. go back to a Product you paid for and review it: rate it, and give it a title and a few words.
   Its stars show on its card and its page, and you can edit or delete your review there. On a
   Product you haven't bought, the page says why you can't review it.

The payment gateway is mocked, and each test card stands for a Payment method:

| Test card | Token | Paying it |
|---|---|---|
| Approve | `tok_approve` | pays: the Order is **Paid** |
| Decline | `tok_decline` | is declined: "Your card was declined" |
| Insufficient funds | `tok_insufficient_funds` | is declined for insufficient funds |
| Gateway error | `tok_gateway_error` | fails at the gateway, and no Payment is recorded |

A checkout left unpaid expires after 15 minutes, and the Storefront offers to start again. Its
Reservation stops holding the Stock 2 minutes later, and Inventory's sweeper marks it `RELEASED`
within 30 seconds of that ([Checkout ADR 0001](./services/checkout-pricing/docs/adr/0001-checkout-sessions-hold-stock-through-reservations.md)).
`GET http://localhost:8000/api/inventory/stock/{variantId}` shows the Stock held and given back.

Staff work in the [Admin Console](./frontend/admin-console/README.md) on http://localhost:8090:
sign in as `staff@ecomm.local` / `staff` to create and edit Products with their Variants, Prices
and Stock, and Categories with their attribute definitions. A new Product, or a changed Price, shows in the
Storefront's listing within a second or two, once [Search & Discovery](./services/search-discovery/README.md)
has the event, and on its own page at once. The demo Customer is refused there.

Every failed request on a Storefront page shows a support reference: the request's
[Correlation ID](./CONTEXT.md). The services log JSON lines carrying it, so one request can be
followed across them:

```sh
docker compose logs --no-log-prefix | grep '<Correlation ID>'
```

### Ports

The browser reaches the services only through the [API gateway](./platform/api-gateway/README.md),
which the Storefront's and the Admin Console's nginx send `/api/` to. Each service also publishes
a host port, which bypasses the gateway, for development only (see each service's README):

| Port | |
|---|---|
| 8080 | Storefront |
| 8090 | Admin Console |
| 8000 | API gateway |
| 8180 | Keycloak (`admin` / `admin`) |
| 8081–8086 | Catalog, Inventory, Cart, Payment, Order Management and Checkout & Pricing |
| 8087 | Promotions |
| 8089 | Search & Discovery |
| 8097 | Reviews & Ratings |
| 8088 | Apicurio schema registry: the event schemas, under `/apis/registry/v3` |
| 9092 | Kafka |
| 27017 | Mongo |
| 5050 | pgAdmin, once started (below) |

Override one that is already taken, e.g. `POSTGRES_PORT=5433 make up`; Couchbase's ports, the
Storefront's 8080 and the Admin Console's 8090 are fixed.

### Browsing Postgres

`docker compose up -d pgadmin` starts pgAdmin on http://localhost:5050. It isn't part of `make up`.
It opens without a login and is already connected to the shared Postgres as `ecomm`, so every
service's database (`orders`, `inventory`, `payment`, `promotions`, …) is under **ecomm** in its
tree. Its connection is in [`infra/docker/pgadmin`](./infra/docker/pgadmin). It keeps nothing
between restarts, so saved queries and layout are lost when the container is recreated.

### The event backbone

Kafka, Mongo and the Apicurio schema registry run in the default stack. On every `make up`, two
steps run once and exit before anything uses them ([ADR 0006](./docs/adr/0006-kafka-as-single-event-backbone.md)):

- `kafka-topics` creates a topic for each event schema in [`platform/event-schemas`](./platform/event-schemas/README.md):
  `order-management.order`, `inventory.stock`, `catalog.product` and `catalog.category`. Each is
  log-compacted, with 6 partitions. The broker creates no topics itself, so producing to any other
  topic fails.
- `schema-registry-init` registers the schemas in Apicurio. It fails, and so does `make up`, if a
  schema changed in a way that isn't backward compatible.

`docker compose exec kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:19092 --describe`
shows the topics, and `curl localhost:8088/apis/registry/v3/groups/default/artifacts` the schemas.
How a service publishes and consumes events is in the [service template's README](./platform/service-template/README.md#integration-events).

### Smoke tests

The Storefront's smoke test walks the Customer's path in Chromium against the running stack. As the demo Customer it
adds two Products, checks out with the approving test card, expects `PAID` on the confirmation page and checks through the
Inventory API that the Checkout Session reserved their Stock and paying took it off on-hand. It
also pays with a declining card, sees the decline, then pays the same session with the approving
one, applies `WELCOME10` and sees its discount on the `PAID` Order, and registers a new Customer:

```sh
cd frontend/storefront
npm ci && npx playwright install chromium   # once
npm run test:e2e
```

The Admin Console has its own, in which Staff create a Product with two Variants and their Stock
and a Customer picks either on the Storefront: `npm run test:e2e` in `frontend/admin-console`.

### Commands

| Command | |
|---|---|
| `make up` | Build and start the stack, and wait until it is healthy |
| `make down` | Stop the stack, keeping its data |
| `make seed-reset` | Put the seed Categories, Products, Stock, Coupons and Campaigns back and drop every Cart, Checkout Session, Reservation, Order, Payment and review; registered Customers stay. It also empties the event topics and Search's and Reviews' projections, since the reset stores count their events' versions from 1 again |
| `make search-rebuild` | Rebuild Search's projection from the topics ([Search & Discovery](./services/search-discovery/README.md#rebuilding)) |
| `make reviews-rebuild` | Rebuild Reviews' projection of Orders and Products from the topics, keeping the reviews ([Reviews & Ratings](./services/reviews-ratings/README.md#rebuilding)) |

Every checkout takes Stock, so after many smoke-test runs `make seed-reset` refills it. To wipe
everything, Keycloak's users included, run `docker compose down -v`.

### A stack from Sprint 1

`make up` on a stack first started under Sprint 1 rebuilds every service and adds the gateway,
Promotions and the Admin Console. Two things don't update themselves:

- **The seed.** Catalog loads its seed only into an empty bucket, so the multi-Variant Products
  arrive only after `make seed-reset`, once ([Catalog README](./services/catalog/README.md#seed-data)).
  `make up` creates any service's Postgres database that is missing, so Promotions gains its
  `promotions` database, and `WELCOME10`, without a reset.
- **The Keycloak realm.** Keycloak imports the realm only on its first start, so an existing realm
  lacks the `admin-console` client's redirect URIs, and the Admin Console's sign-in fails with
  "Invalid parameter: redirect_uri". Delete the realm and restart Keycloak, or apply the client to
  the running realm with `kcadm`, as the [Identity & Access README](./services/identity-access/README.md#the-admin-console-client)
  shows.

### Memory

Each Spring service is capped at 384 MB, with 60% of it for the heap, Keycloak at 512 MB and each
nginx at 64 MB. Kafka and Mongo are capped at 512 MB each, with a 256 MB heap for Kafka and a
256 MB cache for Mongo, and Apicurio and pgAdmin at 384 MB each. The default stack fits in about 8 GB of Docker
memory; `docker stats` shows what it uses ([Sprint 2's reading](./docs/roadmap.md#sprint-2-weeks-34--harden-the-skeleton)).
Couchbase, Postgres and Redis are uncapped.

## CI

[`.github/workflows/ci.yml`](./.github/workflows/ci.yml) checks every pull request and every push
to `main`, with five jobs:

| Job | What it checks |
|---|---|
| `backend` | `./gradlew check` on JDK 21: Spotless, unit and Testcontainers tests, and `HexagonalRules` |
| `event-schemas` | Registers the event schemas from before the change in a throwaway Apicurio, then the change's own, so a schema change that isn't backward compatible, such as a field made required or removed, fails |
| `frontend (<app>)` | `npm ci`, `lint`, `typecheck`, `test` and `build` for each app in its matrix: the Storefront and the Admin Console |
| `images` | `docker compose build` of every service and frontend image. On a push to `main` it also pushes them to GHCR as `ghcr.io/ondokuzz/ecomm/<service>`, tagged with the commit SHA and `main` |
| `e2e` | `make up`, then the Storefront's and the Admin Console's Playwright suites in Chromium. When it fails, the `e2e-failure` artifact keeps the Playwright reports, traces and `docker compose logs` |

A new frontend app joins the `frontend` job by adding its directory name to `matrix.app`, and its
`frontend (<app>)` check to the required checks below.

Making the checks block a merge is a manual step, done once, since a workflow cannot require
itself. In the repository's **Settings → Branches**, add a branch protection rule (or ruleset) for
`main`, turn on **Require status checks to pass before merging**, and choose `backend`,
`event-schemas`, `frontend (storefront)`, `frontend (admin-console)`, `images` and `e2e`. A check appears in that list only after it has run
once. On a private repository, branch protection needs a paid GitHub plan.

## Layout

- `services/` — one directory per bounded context (see `CONTEXT.md` for the domain terms, `docs/adr/` for why each is shaped the way it is)
- `frontend/storefront`, `frontend/admin-console` — the two customer/staff-facing React apps
- `platform/` — shared, cross-service concerns: `service-commons`, the hexagonal-architecture service starter template, the API gateway, and `event-schemas`, the integration events' JSON Schemas
- `infra/terraform` — infrastructure-as-code for the eventual AWS deployment

## Docs

- [`CONTEXT-MAP.md`](./CONTEXT-MAP.md) — the fifteen bounded contexts, how they relate, and where their docs live
- [`CONTEXT.md`](./CONTEXT.md) — the shared domain glossary
- [`docs/roadmap.md`](./docs/roadmap.md) — the sprint-by-sprint delivery plan
- [`docs/adr/`](./docs/adr/) — system-wide architecture decision records
- Context-specific ADRs live inside the service they belong to, e.g. [`services/catalog/docs/adr/`](./services/catalog/docs/adr/)
