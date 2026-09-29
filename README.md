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
3. add Products to the Cart, go to checkout and press **Pay**. The payment is mocked and always
   succeeds;
4. see the Order confirmation with its Order Status, **Paid**, and find the Order under
   **My Orders**.

The smoke test walks the same path in Chromium against the running stack. As the demo Customer it
adds two Products, checks out, expects `PAID` on the confirmation page and checks through the
Inventory API that Stock went down. It also registers a new Customer:

```sh
cd frontend/storefront
npm ci && npx playwright install chromium   # once
npm run test:e2e
```

| Command | |
|---|---|
| `make up` | Build and start the stack, and wait until it is healthy |
| `make down` | Stop the stack, keeping its data |
| `make seed-reset` | Put the seed Products and Stock back and drop every Cart, Order and Payment; registered Customers stay |

Every checkout takes Stock, so after many smoke-test runs `make seed-reset` refills it. To wipe
everything, Keycloak's users included, run `docker compose down -v`.

Each service publishes a host port: Keycloak on 8180 (`admin` / `admin`), Catalog to Checkout on
8081–8086 (see each service's README). Override one that is already taken, e.g.
`POSTGRES_PORT=5433 make up`; Couchbase's ports and the Storefront's 8080 are fixed. Kafka and
Mongo are behind the `full` profile until Sprint 3 (`docker compose --profile full up -d`).

Each Spring service is capped at 384 MB, with 60% of it for the heap, and Keycloak at 512 MB, so
the default stack fits in about 8 GB of Docker memory.

## CI

[`.github/workflows/ci.yml`](./.github/workflows/ci.yml) checks every push and pull request with
four jobs:

| Job | What it checks |
|---|---|
| `backend` | `./gradlew check` on JDK 21: Spotless, unit and Testcontainers tests, and `HexagonalRules` |
| `frontend (<app>)` | `npm ci`, `lint`, `typecheck`, `test` and `build` for each app in its matrix; today the Storefront |
| `images` | `docker compose build` of every service and frontend image. On a push to `main` it also pushes them to GHCR as `ghcr.io/ondokuzz/ecomm/<service>`, tagged with the commit SHA and `main` |
| `e2e` | `make up`, then the Playwright suite in Chromium. When it fails, the `e2e-failure` artifact keeps the Playwright report, traces and `docker compose logs` |

A new frontend app joins the `frontend` job by adding its directory name to `matrix.app`, and its
`frontend (<app>)` check to the required checks below.

Making the checks block a merge is a manual step, done once, since a workflow cannot require
itself. In the repository's **Settings → Branches**, add a branch protection rule (or ruleset) for
`main`, turn on **Require status checks to pass before merging**, and choose `backend`,
`frontend (storefront)`, `images` and `e2e`. A check appears in that list only after it has run
once. On a private repository, branch protection needs a paid GitHub plan.

## Layout

- `services/` — one directory per bounded context (see `CONTEXT.md` for the domain terms, `docs/adr/` for why each is shaped the way it is)
- `frontend/storefront`, `frontend/admin-console` — the two customer/staff-facing React apps
- `platform/` — shared, cross-service concerns: the event-schema library and the hexagonal-architecture service starter template
- `infra/terraform` — infrastructure-as-code for the eventual AWS deployment

## Docs

- [`CONTEXT-MAP.md`](./CONTEXT-MAP.md) — the fifteen bounded contexts, how they relate, and where their docs live
- [`CONTEXT.md`](./CONTEXT.md) — the shared domain glossary
- [`docs/roadmap.md`](./docs/roadmap.md) — the sprint-by-sprint delivery plan
- [`docs/adr/`](./docs/adr/) — system-wide architecture decision records
- Context-specific ADRs live inside the service they belong to, e.g. [`services/catalog/docs/adr/`](./services/catalog/docs/adr/)
