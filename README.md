# Ecomm Platform

A from-scratch e-commerce platform, architected as fifteen bounded contexts across five parallel team tracks, planned over eight two-week sprints for a small (4-5 person) engineering team. Sprint 1's walking skeleton runs locally: browse Products, keep a Cart, check out with a mock payment and follow the Order.

## Run Sprint 1

You need Docker with about 8 GB of memory, `make`, and Node 24 for the smoke test.

```sh
make up           # build and start the stack; returns once every service is healthy
```

The first build takes several minutes. Then open http://localhost:8080, where you can:

1. browse the 20 seeded Products by category;
2. sign in as `demo@ecomm.local` / `demo`, or register a new Customer on Keycloak's page;
3. add Products to the Cart and go to checkout, which holds them and their Prices for 15 minutes,
   enter the Coupon `WELCOME10` for 10% off, then pick a test card and press **Pay**. The payment is mocked: **Approve** pays, while
   **Decline**, **Insufficient funds** and **Gateway error** each show why paying failed, and you
   can pay again with another card while the hold lasts;
4. see the Order confirmation with its Order Status, **Paid**, and its discount, and find the Order
   under **My Orders**.

Staff work in the [Admin Console](./frontend/admin-console/README.md) on http://localhost:8090:
sign in as `staff@ecomm.local` / `staff` to create and edit Products with their Variants, Prices
and Stock, and Categories with their attribute definitions. The demo Customer is refused there.

The smoke test walks the same path in Chromium against the running stack. As the demo Customer it
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

| Command | |
|---|---|
| `make up` | Build and start the stack, and wait until it is healthy |
| `make down` | Stop the stack, keeping its data |
| `make seed-reset` | Put the seed Categories, Products, Stock and Coupons back and drop every Cart, Reservation, Order and Payment; registered Customers stay |

Every checkout takes Stock, so after many smoke-test runs `make seed-reset` refills it. A stack
first seeded before Catalog had multi-Variant Products needs it once too, since the seed only loads
into an empty Catalog ([Catalog README](./services/catalog/README.md#seed-data)). `make up` creates
any service's Postgres database that is missing, so a stack from before Promotions gains its
`promotions` database, and `WELCOME10`, without a reset. To wipe
everything, Keycloak's users included, run `docker compose down -v`.

The browser reaches the services only through the [API gateway](./platform/api-gateway/README.md),
on 8000, which the Storefront's and the Admin Console's nginx send `/api/` to. Each service also publishes a host port:
Keycloak on 8180 (`admin` / `admin`), Catalog to Checkout on 8081–8086 and Promotions on 8087 (see
each service's README).
Those bypass the gateway, for development only. Override one that is already taken, e.g.
`POSTGRES_PORT=5433 make up`; Couchbase's ports, the Storefront's 8080 and the Admin Console's 8090 are fixed. Kafka and
Mongo are behind the `full` profile until Sprint 3 (`docker compose --profile full up -d`).

Each Spring service is capped at 384 MB, with 60% of it for the heap, and Keycloak at 512 MB, so
the default stack fits in about 8 GB of Docker memory.

## CI

[`.github/workflows/ci.yml`](./.github/workflows/ci.yml) checks every pull request and every push
to `main`, with four jobs:

| Job | What it checks |
|---|---|
| `backend` | `./gradlew check` on JDK 21: Spotless, unit and Testcontainers tests, and `HexagonalRules` |
| `frontend (<app>)` | `npm ci`, `lint`, `typecheck`, `test` and `build` for each app in its matrix: the Storefront and the Admin Console |
| `images` | `docker compose build` of every service and frontend image. On a push to `main` it also pushes them to GHCR as `ghcr.io/ondokuzz/ecomm/<service>`, tagged with the commit SHA and `main` |
| `e2e` | `make up`, then the Storefront's and the Admin Console's Playwright suites in Chromium. When it fails, the `e2e-failure` artifact keeps the Playwright reports, traces and `docker compose logs` |

A new frontend app joins the `frontend` job by adding its directory name to `matrix.app`, and its
`frontend (<app>)` check to the required checks below.

Making the checks block a merge is a manual step, done once, since a workflow cannot require
itself. In the repository's **Settings → Branches**, add a branch protection rule (or ruleset) for
`main`, turn on **Require status checks to pass before merging**, and choose `backend`,
`frontend (storefront)`, `frontend (admin-console)`, `images` and `e2e`. A check appears in that list only after it has run
once. On a private repository, branch protection needs a paid GitHub plan.

## Layout

- `services/` — one directory per bounded context (see `CONTEXT.md` for the domain terms, `docs/adr/` for why each is shaped the way it is)
- `frontend/storefront`, `frontend/admin-console` — the two customer/staff-facing React apps
- `platform/` — shared, cross-service concerns: `service-commons`, the hexagonal-architecture service starter template, and the API gateway
- `infra/terraform` — infrastructure-as-code for the eventual AWS deployment

## Docs

- [`CONTEXT-MAP.md`](./CONTEXT-MAP.md) — the fifteen bounded contexts, how they relate, and where their docs live
- [`CONTEXT.md`](./CONTEXT.md) — the shared domain glossary
- [`docs/roadmap.md`](./docs/roadmap.md) — the sprint-by-sprint delivery plan
- [`docs/adr/`](./docs/adr/) — system-wide architecture decision records
- Context-specific ADRs live inside the service they belong to, e.g. [`services/catalog/docs/adr/`](./services/catalog/docs/adr/)
