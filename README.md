# Ecomm Platform

A from-scratch e-commerce platform, architected as fifteen bounded contexts across five parallel team tracks, planned over nine two-week sprints for a small (4-5 person) engineering team. Sprint 5 has added fulfillment and returns, and it all runs locally: search the Catalog with Facets, pick a Variant, keep a Cart, check out with a Checkout Session that reserves its Stock, gets the running Campaigns by itself and a Coupon on top, and pay through a mock payment that can decline or wait for the bank, which the checkout Saga on Temporal either completes or undoes whole, after giving a Shipping address and choosing a Shipping option. Staff pick and pack the Order from a fulfillment queue, and the fulfillment Saga captures its Payment, hands it to the carrier and follows it to delivery. Then follow the Order's Status history, its Payment and its Shipment's Tracking events, review what you bought, see what others bought with it, and, through the API, ask to return it or claim its warranty. Staff manage the Catalog, with Warranty terms and Serial tracking, Coupons and Campaigns, look up Orders with their Payments and each Variant's Stock movements, and work the fulfillment and RMA approval queues, in an Admin Console. Grafana shows the services, the event consumers and both Sagas.

## Run Sprint 5

You need Docker with about 8 GB of memory, `make`, and Node 24 for the smoke tests.

```sh
make up           # build and start the stack; returns once every service is healthy and the checkout path warm
```

The first build takes several minutes. Then open the Storefront on http://localhost:8080, where you can:

1. search the 20 seeded Products, and narrow them by category, by attribute (storage, brand,
   …), by Price and to what is in stock, with each choice's count beside it; the listing's URL keeps
   the view. The Google Pixel 9, the Apple iPhone 16 and the Apple
   MacBook Air 13 (M3) come in several Variants: pick one by its color and storage, each with its
   own Price and Stock. One iPhone Variant starts sold out;
2. sign in as `demo@ecomm.local` / `demo`, or register a new Customer on Keycloak's page;
3. add Products to the Cart, which warns of a line that asks for more than is in Stock, and go to
   checkout, which holds them and their Prices for 15 minutes
   and shows the time left. An `audio` Product gets the "Audio week" Campaign's 15% off by itself.
   Enter the Coupon `WELCOME10` for 10% off what is left. Give a Shipping address in the EU: each
   field's problem is named (a German postcode is five digits, a Dutch one `1012 AB`), and a valid
   one comes back normalised. Choose **Standard** or **Express**, whose Shipping cost, quoted from
   the Warehouse that will ship it (Amsterdam for the Netherlands, Berlin for anywhere else), joins
   the total. Then pick a test card and press **Pay**.
   Paying starts the checkout Saga in [Orchestration](./services/orchestration/README.md), which
   you can follow step by step in Temporal's UI on http://localhost:8233; a payment that takes more
   than a few seconds shows "Confirming your payment…" until it is done. Paying again with another
   card works while the Checkout Session lasts;
4. see the Order confirmation with its Order Status, **Paid**, its Shipping address and option, its timeline drawn from the Order
   Status history (Placed, then Paid, each with its time), each of its Discounts and its Payment,
   **Authorized**, and find the Order under **My Orders**. A cancelled Order says why: its payment
   was declined, or its Payment voided, releasing the hold on the card. Once Staff pack it, the
   Order page shows its Shipment: carrier, tracking number and Tracking events, refreshed every 10
   seconds while it is Shipped, and **Delivered** about 30 seconds after packing, its Payment
   **Captured**;
5. go back to a Product you paid for and review it: rate it, and give it a title and a few words.
   Its stars show on its card and its page, and you can edit or delete your review there. On a
   Product you haven't bought, the page says why you can't review it. A review with a screened
   word, such as "scam", awaits moderation and isn't shown until Staff publish it, and you can
   report another Customer's review as spam, offensive, off-topic or for another reason;
6. see, under a Product's page, what Customers also bought with it: the Products most often in the
   same paid Orders, from [Recommendations](./services/recommendations/README.md), and its Warranty
   terms, such as "24 months, manufacturer's warranty (Samsung)";
7. once an Order is delivered, request an RMA for it through the API: a return within the 14-day
   Return Period, or a warranty claim within the Warranty Window, naming the IMEI or serial number
   of a serial-tracked unit ([Returns & Warranty](./services/returns-warranty/README.md) and its
   [`.http` file](./services/returns-warranty/http/returns-warranty.http)). Once Staff approve it,
   the returns Saga in [Orchestration](./services/orchestration/README.md#the-returns-saga) awaits
   its units, then refunds, replaces or closes it as its Inspection says. The Storefront's form
   comes in Sprint 6.

The payment gateway is mocked, and each test card stands for a Payment method:

| Test card | Token | Paying it |
|---|---|---|
| Approve | `tok_approve` | pays: the Order is **Paid** |
| Decline | `tok_decline` | is declined: "Your card was declined" |
| Insufficient funds | `tok_insufficient_funds` | is declined for insufficient funds |
| Gateway error | `tok_gateway_error` | fails at the gateway, and no Payment is recorded; the Saga tries 4 times over about 10 seconds, then says the payment didn't go through |
| Bank confirms, approves | `tok_async_approve` | shows "Confirming your payment…" until the mock gateway's webhook approves it about 3 seconds later, then pays |
| Bank confirms, declines | `tok_async_decline` | shows "Confirming your payment…", then "Your card was declined" once the webhook declines it |
| Charge refused | `tok_capture_decline` | pays: the Order is **Paid**, but the mock gateway refuses to capture the Payment, as `authorization_expired` |

A checkout left unpaid expires after 15 minutes, and the Storefront offers to start again. Its
Reservation stops holding the Stock 2 minutes later, and Inventory's sweeper marks it `RELEASED`
within 30 seconds of that ([Checkout ADR 0001](./services/checkout-pricing/docs/adr/0001-checkout-sessions-hold-stock-through-reservations.md)).
`GET http://localhost:8000/api/inventory/stock/{variantId}` shows the Stock held and given back.
If the hold runs out while the bank is still confirming a payment, the Saga voids the authorization
and cancels the Order, and the Storefront says the hold ran out and nothing was charged.

Staff work in the [Admin Console](./frontend/admin-console/README.md) on http://localhost:8090,
signed in as `staff@ecomm.local` / `staff`; the demo Customer is refused there. Staff can:

1. find a Customer's Order under **Orders**, by its Order reference (`#3F2A9C1B`), Customer ID,
   Status or when it was placed, and open it to see its lines, Discounts, tax and total, and its
   Status history with each change's time and caller, and its Payments with their Payment
   transactions: each authorization and void, with its outcome, amount, gateway reference and time.
   Nothing there changes an Order or a Payment;
2. under **Promotions**, manage Coupons, with their Usage limits, redemptions, holds and state
   (active, scheduled, expired, off or exhausted), and Campaigns with their state (running,
   scheduled, over or off). A Campaign created there, say 20% off `laptops`, applies to the next Checkout Session
   with a laptop in it;
3. create and edit Products with their Variants, Prices and Stock, and Categories with their
   attribute definitions. A new Product, or a changed Price, shows in the Storefront's listing within
   a second or two, once [Search & Discovery](./services/search-discovery/README.md) has the event,
   and on its own page at once. A Product's editor shows each Variant's On-hand, reserved and
   available Stock, and its Stock movements, newest first, with whether they balance. It also sets a
   Product's Warranty terms (months and manufacturer) and Serial tracking (none, serial number or
   IMEI);
4. under **Fulfillment**, work the queue of Shipments by Warehouse and status, oldest first: pick
   one, then pack it, entering the IMEI or serial number of each serial-tracked unit, checked as they
   type. Packing lets the fulfillment Saga in [Orchestration](./services/orchestration/README.md)
   capture the Payment, mark the Order **Fulfilled**, hand the Shipment to the mock carrier and mark
   it **Shipped** with a tracking number, then **Delivered** once the carrier's signed webhooks say
   so. Paid with `tok_capture_decline`, the Shipment goes on hold with "Capture declined:
   authorization_expired", and the Order stays **Paid**;
5. under **RMAs**, approve or reject each requested RMA with a note, each decision kept in its
   history; an Order's page lists its Shipment and its RMAs;
6. under **Reviews**, work the Review moderation queue of held and reported reviews, oldest first:
   publish a held one, dismiss a published one's reports, or remove either with a reason its author
   sees ([Reviews & Ratings](./services/reviews-ratings/README.md#moderation)).

Every failed request on a Storefront page shows a support reference: the request's
[Correlation ID](./GLOSSARY.md). The services log JSON lines carrying it, and the events a request
causes carry it to their consumers, so one request can be followed across them, through Kafka too:

```sh
docker compose logs | grep '<Correlation ID>'
```

Paying on the Storefront, for instance, logs it in the Storefront's nginx, the gateway and
Checkout, then in Orchestration's checkout Saga and in Order Management, Payment, Inventory, Cart
and Checkout again as the Saga calls them, and last in
Reviews & Ratings' and Recommendations' consumers as they apply the `order-management.order` events
(`Applied Order … version 2`), and in Search & Discovery's as it applies the `inventory.stock` one.

### Ports

The browser reaches the services only through the [API gateway](./platform/api-gateway/README.md),
which the Storefront's and the Admin Console's nginx send `/api/` to. Each service also publishes
a host port, which bypasses the gateway, for development only (see each service's README), except
Orchestration, which serves nothing but its health:

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
| 8098 | Recommendations |
| 8099 | Fulfillment & Shipping |
| 8100 | Returns & Warranty |
| 8101 | Notifications |
| 8025 | Mailpit: every email the stack sends, and its API (`/api/v1/messages`); its SMTP port is 1025 |
| 8088 | Apicurio schema registry: the event schemas, under `/apis/registry/v3` |
| 9092 | Kafka |
| 7233 | Temporal, for workers and clients run from the host ([Orchestration](./services/orchestration/README.md#temporal)) |
| 8233 | Temporal's web UI: every workflow, its history and its retries |
| 3000 | Grafana: the Services, Event consumers and Sagas dashboards, open for viewing ([below](#metrics-and-dashboards)) |
| 9090 | Prometheus: every service's metrics, and Kafka's consumer lag, for 2 days |
| 27017 | Mongo |
| 5050 | pgAdmin, once started (below) |

Override one that is already taken, e.g. `POSTGRES_PORT=5433 make up`; Couchbase's ports, the
Storefront's 8080 and the Admin Console's 8090 are fixed.

### Browsing Postgres

`docker compose up -d pgadmin` starts pgAdmin on http://localhost:5050. It isn't part of `make up`.
It opens without a login and is already connected to the shared Postgres as `ecomm`, so every
service's database (`orders`, `inventory`, `payment`, `promotions`, …) is under **ecomm** in its
tree. Its connection is in [`infra/docker/pgadmin`](./infra/docker/pgadmin), and its password file
is written from the stack's Postgres password as it starts ([Secrets](#secrets)). It keeps nothing
between restarts, so saved queries and layout are lost when the container is recreated.

### Metrics and dashboards

Every Spring service, the gateway included, serves its metrics on `/actuator/prometheus`
([service template](./platform/service-template/README.md#metrics)). Prometheus scrapes them every
15 seconds and keeps 2 days of them, and Grafana shows them on http://localhost:3000, open to
anyone for viewing (sign in as `admin`, with the password in
`.secrets/ecomm/grafana-admin-password`, to explore). Its data source and its three
dashboards, under **ecomm**, are files in [`infra/docker/grafana`](./infra/docker/grafana):

- **Services:** request rate, 5xx rate and p95 latency per service, request rate per route,
  each Postgres service's outbox events not yet sent to Kafka (Catalog's Couchbase outbox isn't
  measured), Inventory's Stock cache: its hit ratio, and its hits, misses and Redis errors, and
  Cart's Abandoned Carts, per hour.
- **Event consumers:** lag per consumer group, and per group, topic and partition; consumption
  rate; retries; dead-lettered and redriven events. Lag comes from a Kafka exporter, which reads
  each group's committed offsets from the broker, so it keeps showing while a group is stopped or
  stuck retrying an event. The consumers' own lag, which counts only from what they have fetched,
  isn't plotted.
- **Sagas:** a Checkout row, with checkouts by outcome (`PAID`, `DECLINED`, `HOLD_EXPIRED`,
  `FAILED`), step failures by step, compensations by kind, and Temporal's own activity failures and
  workflow durations; and a Fulfillment row, with fulfillments by outcome (`DELIVERED`,
  `CAPTURE_REFUSED`, `DELIVERY_UNCONFIRMED`, `FAILED`), step failures by step, Shipments on hold by
  kind, the lag of Orchestration's `orchestration.orders`, `orchestration.shipments` and
  `orchestration.rmas` groups, and workflow durations; and a Returns row, with returns by outcome
  (`REFUNDED`, `EXCHANGED`, `CLOSED`, `EXPIRED`, `REFUND_REFUSED`), step failures by step, RMAs on
  hold by kind, and Temporal's own activity failures.

A panel plotting `rate()` or `increase()` counts a series only from its second sample, so a
counter has to be on `/actuator/prometheus` at 0 before its first count, or that count is missed.
Orchestration's checkout counters, Inventory's Stock cache counters and Cart's `cart_abandoned_total` are. Some can't be, since
what they are tagged with is known only when they count, so their panels miss the first count of
each new series after a service starts: **Retries** (per group and topic), the **5xx rate** and
**request rate per route** (per route and status), and Temporal's own **activity failures** and
**workflow durations**. The dead-lettered and redriven events are plotted as counters since each
service started for the same reason.

A dashboard changed in Grafana can't be saved there: export its JSON and replace the file.

### The event backbone

Kafka, Mongo and the Apicurio schema registry run in the default stack. On every `make up`, two
steps run once and exit before anything uses them ([ADR 0006](./docs/adr/0006-kafka-as-single-event-backbone.md)):

- `kafka-topics` creates a topic for each event schema in [`platform/event-schemas`](./platform/event-schemas/README.md):
  `order-management.order`, `inventory.stock`, `catalog.product`, `catalog.category` and
  `payment.payment`. Each is
  log-compacted, with 6 partitions. It also creates a dead-letter topic, `<group>.dlt`, and a
  redrive topic, `<group>.redrive`, for each consumer group in
  [`infra/docker/consumer-groups`](./infra/docker/consumer-groups): not compacted, kept for 14
  days, with 6 partitions and no schema. The broker creates no topics itself, so producing to any
  other topic fails.
- `schema-registry-init` registers the schemas in Apicurio. It fails, and so does `make up`, if a
  schema changed in a way that isn't backward compatible.

`docker compose exec kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:19092 --describe`
shows the topics, and `curl localhost:8088/apis/registry/v3/groups/default/artifacts` the schemas.
How a service publishes and consumes events is in the [service template's README](./platform/service-template/README.md#integration-events).

### Smoke tests

The Storefront's smoke test walks the Customer's path in Chromium against the running stack, each
test as a Customer it makes in Keycloak and deletes afterwards. It adds two Products, checks out
to a German address, first with a malformed postcode it sees named, then corrected, with Express
in the total, pays with the approving test card, expects `PAID` and an authorized Payment on the confirmation page, and
checks through the Inventory API that the Checkout Session reserved their Stock and paying took it
off on-hand. It also pays with a declining card, sees the decline, then pays the same session with
the approving one, applies `WELCOME10` and sees its discount on the `PAID` Order, gets Audio week
and `WELCOME10` together on an audio Product, pages through My Orders (stood in for), and registers
a new Customer. Then it walks Sprint 3's definition of done: it filters Audio by type and to what is
in stock, searches, checks out with Audio week and `WELCOME10`, pays, sees Placed then Paid on the
Order's timeline, reviews the Product and sees its Rating summary on its card and page. Another
Customer is told they can't review a Product they never bought. Then Sprint 4's: a Customer buys two
new Products together and sees each under "Customers also bought" on the other's page, and pays with
each card the bank confirms, seeing "Confirming your payment…" and then the paid Order, its Payment
approved by the gateway's webhook, or the decline's reason, after which the same session is paid
with the approving card. Then Sprint 5's: a Product page shows its Warranty terms, and once Staff
pack a paid Order its page shows the Shipment's Tracking events, then Delivered, and the Payment
captured; an Order without a Shipment shows none:

```sh
cd frontend/storefront
npm ci && npx playwright install chromium   # once
npm run test:e2e
```

The Admin Console has its own: Staff create a Product with two Variants and their Stock, and a
Customer picks either on the Storefront; Staff set a Variant's Stock and see its Stock movements,
balanced; Staff find a Customer's Order and see its history, and open a paid Order and see its
Payment's authorization; Staff
manage Coupons and Campaigns, and a Campaign they create applies to a Customer's next checkout,
whose Order Staff then find; Staff give a Product Warranty terms and Serial tracking and the
Storefront shows the warranty; Staff pick and pack a Shipment from the fulfillment queue, refused an
invalid IMEI, and see it dispatched, and a Shipment whose capture is refused goes on hold with the
reason; Staff approve a return and reject a warranty claim from the RMA queue; and Staff receive a returned phone, refused a wrong IMEI, inspect it, and see the returns Saga refund it. Run it with `npm run test:e2e` in `frontend/admin-console`, after the
Storefront's rather than beside it, since both check out. Both suites make their own Customers
through Keycloak's admin API and delete them again; the Orders they place, and the Stock those
take, stay. The tests pick from the seeded Products only, so an `E2E-` Product a failed test left
behind doesn't throw them.

Each suite runs its spec files side by side, one Playwright worker each. Where Docker's memory is short of what
the stack uses, or the host swaps Docker's VM out, requests stall for tens of seconds and tests time
out; `npx playwright test --workers=1` loads the stack less, and is how both suites passed on a
16 GB laptop on 2026-10-17 ([Sprint 5's note](./docs/roadmap.md#sprint-5-weeks-910--fulfillment--returns-foundations)).

### Secrets

No secret is in the repo, an image or the Compose file. `make secrets`, which `make up` runs first,
writes each secret the stack lacks to `.secrets/<project>/` (`.secrets/ecomm/` for the default
stack), which is git-ignored and which only you can open (0700). On macOS each file is 0600 as
well, and `make secrets` tightens one an earlier run left at 0644; on Linux the files stay 0644,
since a container's user isn't their owner there and couldn't read them otherwise. Compose mounts
each container only the ones it needs, as files in `/run/secrets`; a Spring service reads them as
properties, through its config tree ([ADR 0011](./docs/adr/0011-secrets-as-generated-files.md)).

| Secret | Used by |
|---|---|
| `postgres-password` | Postgres's `ecomm` login: every Postgres service, Keycloak, Temporal, Apicurio, pgAdmin |
| `couchbase-password` | Couchbase's `admin`: Catalog, and `make seed-reset` |
| `keycloak-admin-password` | Keycloak's bootstrap `admin` in the `master` realm, and realm sync |
| `grafana-admin-password`, `pgadmin-password` | Grafana's and pgAdmin's `admin` |
| `checkout-client-secret`, `orchestration-client-secret`, `notifications-client-secret` | The confidential Keycloak clients and the services that use them; realm sync sets each client's secret from its file on every start ([Identity & Access](./services/identity-access/README.md#client-secrets)) |
| `payment-webhook-secret` | Payment, and its mock gateway, which signs its webhooks with it |
| `carrier-webhook-secret` | Fulfillment & Shipping, and its mock carrier, which signs its Carrier webhooks with it |

A new secret is 32 random bytes, URL-safe. An existing file is never overwritten. On a stack whose
volumes were made before Sprint 5, the infrastructure passwords are written with the dev values
they were made with (`ecomm`, `password` and `admin`), since Postgres, Couchbase and Keycloak keep
the password they started with; the client and webhook secrets are new on every stack. So never
delete an infrastructure password's file while the stack's volumes exist: `make secrets` would write
the old dev value back, which a stack made since doesn't have. `docker compose down -v` removes the
volumes but not `.secrets/`, and a fresh stack starts with the passwords that are there.

The demo Customer's and the Staff user's passwords and the public `dev-cli` client stay in the
realm file: they are demo data for trying the stack and for the tests, not secrets.

Rotation is by hand. A client or webhook secret: delete its file, run `make secrets`, and recreate
what reads it, e.g. for Checkout's client:

```sh
rm .secrets/ecomm/checkout-client-secret && make secrets
docker compose up -d --force-recreate --wait keycloak-realm checkout-pricing
```

An infrastructure password: change it where it lives first, then write the same value to its file,
then recreate everything, e.g. for Postgres:

```sh
new=$(head -c 32 /dev/urandom | base64 | tr '+/' '-_' | tr -d '=\n')
docker compose exec -T postgres psql -U ecomm -d postgres -c "ALTER ROLE ecomm PASSWORD '$new'"
printf '%s' "$new" > .secrets/ecomm/postgres-password
docker compose up -d --force-recreate --wait
```

Couchbase's is changed with `couchbase-cli setting-cluster --cluster-password` and Keycloak's admin
with `kcadm.sh set-password`, both run in their containers. Grafana and pgAdmin keep no data, so
deleting their file and recreating them is enough.

The `.http` files' client and webhook secrets come from the IntelliJ HTTP client's `dev`
environment. Write it, git-ignored, at the repo root:

```sh
d=.secrets/ecomm; printf '{"dev": {"checkoutClientSecret": "%s", "orchestrationClientSecret": "%s", "paymentWebhookSecret": "%s", "carrierWebhookSecret": "%s"}}\n' \
  "$(cat $d/checkout-client-secret)" "$(cat $d/orchestration-client-secret)" "$(cat $d/payment-webhook-secret)" \
  "$(cat $d/carrier-webhook-secret)" \
  > http-client.private.env.json
```

### Commands

| Command | |
|---|---|
| `make up` | Write the secrets the stack lacks, build and start the stack, wait until it is healthy, and warm the checkout path ([`warm-up.py`](./infra/docker/warm-up.py)) |
| `make secrets` | Write each secret the stack lacks to `.secrets/<project>/`, never overwriting one ([Secrets](#secrets)) |
| `make down` | Stop the stack, keeping its data |
| `make status` | Say whether every service is running and healthy, naming any that isn't with its exit code and whether it was killed for its memory cap. Both Playwright suites run it first |
| `make seed-reset` | Put the seed Categories, Products, Stock, Coupons and Campaigns back and drop every Cart, Checkout Session, Reservation, Order, Payment, Shipment, RMA, review and Notification; registered Customers stay. It also empties the event topics, dead-letter and redrive topics included, and Search's, Reviews', Recommendations', Fulfillment & Shipping's, Returns & Warranty's and Notifications' projections, since the reset stores count their events' versions from 1 again |
| `make upgrade-from FROM=<commit>`, `make upgrade-to`, `make upgrade-clean` | Start the stack as it was at a commit under a project of its own, bring it up to this tree keeping its data, and remove it ([the upgrade check](./docs/agents/upgrade-check.md)) |
| `make search-rebuild` | Rebuild Search's projection from the topics ([Search & Discovery](./services/search-discovery/README.md#rebuilding)) |
| `make dlt-redrive GROUP=<group>` | Redrive a consumer group's dead events once their cause is fixed: copy what reached `<group>.dlt` since the last redrive to `<group>.redrive`, which the group's listeners also consume ([service template](./platform/service-template/README.md#consuming)) |
| `make reviews-rebuild` | Rebuild Reviews' projection of Orders and Products from the topics, keeping the reviews ([Reviews & Ratings](./services/reviews-ratings/README.md#rebuilding)) |
| `make recommendations-rebuild` | Rebuild Recommendations' projection of Orders and Products from the topics ([Recommendations](./services/recommendations/README.md#rebuilding)) |
| `make fulfillment-rebuild` | Rebuild Fulfillment & Shipping's Product projection from `catalog.product`, keeping the Shipments ([Fulfillment & Shipping](./services/fulfillment-shipping/README.md#shipments)) |
| `make returns-rebuild` | Rebuild Returns & Warranty's projections of Shipments, Products and Orders from the topics, keeping the RMAs ([Returns & Warranty](./services/returns-warranty/README.md#rebuilding)) |
| `make notifications-rebuild` | Rebuild Notifications' projection of Product names from `catalog.product`, keeping the Notification ledger and sending nothing again ([Notifications](./services/notifications/README.md#the-events-it-consumes)) |

Every checkout takes Stock, so after many smoke-test runs `make seed-reset` refills it. To wipe
everything, Keycloak's users included, run `docker compose down -v`.

### A stack from Sprint 4

`make up` on a stack from Sprint 4 brings it into Sprint 5 with nothing done by hand; building this
tree's images takes about 20 minutes. It adds Fulfillment & Shipping and Returns & Warranty, and
creates their databases in Postgres (`fulfillment_shipping`, `returns_warranty`). The topics init
adds `fulfillment-shipping.shipment` and `returns-warranty.rma`, and the new groups' dead-letter and
redrive topics ([The event backbone](#the-event-backbone)). Then:

- **Secrets become files.** `make secrets` writes the infrastructure passwords with the dev values
  the stack's volumes were made with (`ecomm`, `password` and `admin`), so Postgres, Couchbase and
  Keycloak keep them, and new client and webhook secrets ([Secrets](#secrets)).
- **The realm takes the new client secrets.** The `keycloak-realm` step sets the `checkout` and
  `orchestration` clients' secrets from their files, so Sprint 4's `checkout-dev-secret` and
  `orchestration-dev-secret` stop working, and the services' tokens keep working with the new ones.
- **Seeded Products gain Warranty terms and Serial tracking**, and publish `catalog.product` as
  `UPDATED`; a Product Staff edited keeps their edits ([Catalog](./services/catalog/README.md)).
- **Paid Orders stay Paid.** Orders paid before the upgrade have no Shipping address: they start no
  fulfillment Saga, have no Shipment, and stay `PAID` with their authorized Payment, never captured
  ([Orchestration](./services/orchestration/README.md)).
- **Checkouts carry on.** A Checkout Session started before the upgrade can still be paid while it
  lasts, once it has a Shipping address and option (paying it first is a 409
  `shippingIncomplete`); its Order is fulfilled like any other.

The upgrade from Sprint 4's definition-of-done commit was checked on 2026-10-17 and 18
([Sprint 5's note](./docs/roadmap.md#sprint-5-weeks-910--fulfillment--returns-foundations)). Run the Playwright suites against an upgraded stack with `COMPOSE_PROJECT_NAME=ecomm-upgrade`;
where host requests reach containers from a public address, its Keycloak's `master` realm first
needs `sslRequired` set to `NONE` (`kcadm.sh update realms/master -s sslRequired=NONE`, run in the
Keycloak container), or the suites' admin tokens are refused with "HTTPS required". A
stack from Sprint 3 or earlier should need only `make up` too, since each sprint's migrations and
backfills run in turn, but only the upgrade from Sprint 4 was checked. A stack from Sprint 1 also
needs `make seed-reset` once for the multi-Variant Products, and the realm's `admin-console`
client, or the Admin Console's sign-in fails with "Invalid parameter: redirect_uri"
([Identity & Access README](./services/identity-access/README.md#the-admin-console-client)).

### Memory

Each Spring service is capped at 384 MB, with 60% of it for the heap, except Orchestration, capped
at 512 MB with 45% (the same 230 MB heap, and room for its Temporal and Kafka clients). On the local
stack they run with only the C1 compiler, 512 KB thread stacks and two malloc arenas, which takes
about 50 MB off each; the images themselves keep the JVM's defaults (`x-spring-jvm` in
[`infra/docker/compose.yaml`](./infra/docker/compose.yaml)). Keycloak is capped at 768 MB with 40%
for the heap, and each nginx at 64 MB. Kafka is capped at 640 MB with a 256 MB heap, and Mongo at
512 MB with a 256 MB cache, Apicurio and pgAdmin at 384 MB each, the Temporal server at 384 MB, its
UI at 64 MB, Prometheus and Grafana at 256 MB each, Mailpit at 64 MB, the Kafka exporter at 32 MB,
and the init steps at 256 MB or less. Couchbase, Postgres and Redis are uncapped. After both suites
the default stack uses about 5.65 GB (`docker stats`), and fits in about 8 GB of Docker memory, with
its swap nearly full ([Sprint 6's reading](./docs/roadmap.md#sprint-6-weeks-1112--returns-completion)).
`make memory` reports it, and [`docs/agents/memory-reading.md`](./docs/agents/memory-reading.md)
says how a reading is taken.
That leaves little over: with 8 GB given to Docker on a 16 GB host that also runs a browser and an
IDE, the host swaps the VM out, and requests stall for tens of seconds.

Building the images takes about 1.3 GB more while it runs, so on 8 GB, `make down` before a
`make up` that rebuilds many of them: built beside the running stack, they can run Docker out of
memory, and the kernel then kills a container, often Kafka or Keycloak. `make status` names it as
killed for memory under its cap, since Docker doesn't say which ran out, and `make up` starts it
again. The services' images build from the Gradle build alone (`.dockerignore`), so editing the
Compose file, the Makefile, a doc or a `.http` file, or running Playwright, doesn't rebuild them.

The Gradle tests' Testcontainers (Postgres, Kafka, Apicurio and others) don't fit beside the running
stack either: with it up, their containers time out starting. `docker compose stop` before
`./gradlew check`, which keeps the stack's data, and `make up` after.

## CI

[`.github/workflows/ci.yml`](./.github/workflows/ci.yml) checks every pull request and every push
to `main`, with six jobs:

| Job | What it checks |
|---|---|
| `backend` | `./gradlew check` on JDK 21: Spotless, unit and Testcontainers tests, and `HexagonalRules` |
| `event-schemas` | Registers the event schemas from before the change in a throwaway Apicurio, then the change's own, so a schema change that isn't backward compatible, such as a field made required or removed, fails |
| `secrets` | Scans the repo and its history with [Gitleaks](https://github.com/gitleaks/gitleaks), by its own rules and [`.gitleaks.toml`](./.gitleaks.toml)'s, which adds any literal password or secret in a configuration file, and fails on a finding. The known dev placeholders of earlier sprints are allow-listed one by one, by fingerprint, in [`.gitleaksignore`](./.gitleaksignore). Then `make secrets` and a check that Compose's resolved configuration holds none of the generated values |
| `frontend (<app>)` | `npm ci`, `lint`, `typecheck`, `test` and `build` for each app in its matrix: the Storefront and the Admin Console |
| `images` | `docker compose build` of every service and frontend image. On a push to `main` it also pushes them to GHCR as `ghcr.io/ondokuzz/ecomm/<service>`, tagged with the commit SHA and `main` |
| `e2e` | `make up`, then the Storefront's and the Admin Console's Playwright suites in Chromium. When it fails, the `e2e-failure` artifact keeps the Playwright reports, traces and `docker compose logs` |

Before a commit, [`.githooks/pre-commit`](./.githooks/pre-commit) runs the same checks on what is
staged: each frontend app's `lint` (oxlint and Prettier's check) and `typecheck`, and Spotless when
Java or Gradle files are staged. Enable it once per clone with `git config core.hooksPath .githooks`;
`npm run format` in an app fixes what Prettier finds.

A new frontend app joins the `frontend` job by adding its directory name to `matrix.app`, and its
`frontend (<app>)` check to the required checks below.

Making the checks block a merge is a manual step, done once, since a workflow cannot require
itself. In the repository's **Settings → Branches**, add a branch protection rule (or ruleset) for
`main`, turn on **Require status checks to pass before merging**, and choose `backend`,
`event-schemas`, `secrets`, `frontend (storefront)`, `frontend (admin-console)`, `images` and `e2e`. A check appears in that list only after it has run
once. On a private repository, branch protection needs a paid GitHub plan.

## Layout

- `services/` — one directory per bounded context (see `GLOSSARY.md` for the domain terms, `docs/adr/` for why each is shaped the way it is)
- `frontend/storefront`, `frontend/admin-console` — the two customer/staff-facing React apps
- `platform/` — shared, cross-service concerns: `service-commons`, the hexagonal-architecture service starter template, the API gateway, and `event-schemas`, the integration events' JSON Schemas
- `infra/terraform` — infrastructure-as-code for the eventual AWS deployment

## Docs

- [`GLOSSARY-MAP.md`](./GLOSSARY-MAP.md) — the fifteen bounded contexts, how they relate, and where their docs live
- [`GLOSSARY.md`](./GLOSSARY.md) — the shared domain glossary
- [`docs/architecture.md`](./docs/architecture.md) — Mermaid diagrams of the requests, the events and the checkout Saga
- [`docs/roadmap.md`](./docs/roadmap.md) — the sprint-by-sprint delivery plan
- [`docs/adr/`](./docs/adr/) — system-wide architecture decision records
- Context-specific ADRs live inside the service they belong to, e.g. [`services/catalog/docs/adr/`](./services/catalog/docs/adr/)
