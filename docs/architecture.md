# Architecture

Four views of the running stack as of Sprint 4: how browsers reach the services, how the contexts
call each other, what flows over Kafka, and how the checkout Saga pays for a Checkout Session. [`GLOSSARY-MAP.md`](../GLOSSARY-MAP.md) is the
source of truth for the relationships. Update these diagrams when a relationship there changes.

## Requests

Browsers reach the services only through the API gateway, which rejects a missing or invalid token
at the edge ([ADR 0010](./adr/0010-api-gateway-authenticates-at-the-edge.md)). Every service still
checks the Keycloak-issued JWT itself. Each service shows the store it owns. The gateway routes
nothing to Payment except the payment gateway's webhook.

```mermaid
flowchart LR
  browser(["Browser"])

  subgraph ui["UI (nginx)"]
    storefront["Storefront"]
    admin["Admin Console"]
  end

  gateway["API gateway"]
  keycloak[("Keycloak<br/>realm ecomm")]
  bank(["Payment gateway<br/>(mock)"])

  subgraph contexts["Bounded contexts"]
    catalog["Catalog<br/><i>Couchbase</i>"]
    search["Search & Discovery<br/><i>MongoDB</i>"]
    reviews["Reviews & Ratings<br/><i>MongoDB</i>"]
    recs["Recommendations<br/><i>Postgres</i>"]
    cart["Cart<br/><i>Redis</i>"]
    checkout["Checkout & Pricing<br/><i>Redis</i>"]
    promotions["Promotions<br/><i>Postgres</i>"]
    inventory["Inventory<br/><i>Postgres + Redis cache</i>"]
    orders["Order Management<br/><i>Postgres</i>"]
    payment["Payment<br/><i>Postgres</i>"]
  end

  browser --> storefront & admin
  browser -. sign in .-> keycloak
  storefront & admin -- "/api/" --> gateway
  gateway -. validates JWT .-> keycloak

  gateway --> catalog & search & reviews & recs & cart & checkout & promotions & inventory & orders
  bank -- "signed webhook" --> gateway
  gateway -- "webhook only" --> payment
```

## Calls between contexts

Contexts call each other's internal endpoints, which the gateway never routes, each with its own
client-credentials identity
([Identity ADR 0002](../services/identity-access/docs/adr/0002-service-identity-by-client-credentials.md)).
Checkout calls four contexts to start and change a Checkout Session, and starts the checkout Saga
to pay one. The Saga's workers then send each step's command.

```mermaid
flowchart TB
  checkout["Checkout & Pricing"]

  subgraph session["Starting and changing a Checkout Session"]
    direction LR
    cart["Cart"]
    catalog["Catalog"]
    promotions["Promotions"]
    inventory["Inventory"]
  end

  subgraph saga["Orchestration"]
    direction LR
    temporal[("Temporal<br/><i>Postgres</i>")]
    orchestration["Orchestration workers"]
  end

  subgraph paying["Paying"]
    direction LR
    orders["Order Management"]
    payment["Payment"]
    bank(["Payment gateway<br/>(mock)"])
  end

  checkout -- reads Cart --> cart
  checkout -- prices lines --> catalog
  checkout -- evaluates Discounts --> promotions
  checkout -- reserves / releases Stock --> inventory
  promotions -- "reads Categories,<br/>currencies" --> catalog

  checkout == "starts checkout Saga,<br/>reads outcome" ==> temporal
  temporal <--> orchestration
  orchestration -- "place, cancel,<br/>mark paid" --> orders
  orchestration -- "authorize, await<br/>settlement, void" --> payment
  payment -- authorizes --> bank
  orchestration -- commit Reservation --> inventory
  orchestration -- clear Cart --> cart
  orchestration -- end session --> checkout
```

## Events

Every event is published from its context's outbox to a compacted topic named after its schema
in [`platform/event-schemas`](../platform/event-schemas/)
([ADR 0002](./adr/0002-ledgers-and-outboxes-not-event-sourcing.md),
[ADR 0006](./adr/0006-kafka-as-single-event-backbone.md)). Consumers keep the newest version of
each entity and never call the publisher. Each consumer group, listed in
[`infra/docker/consumer-groups`](../infra/docker/consumer-groups), has its own `<group>.dlt` and
`<group>.redrive` topics. Commands never travel over Kafka.

```mermaid
flowchart LR
  catalog["Catalog"]
  inventory["Inventory"]
  orders["Order Management"]
  payment["Payment"]

  subgraph kafka["Kafka"]
    tProduct[["catalog.product"]]
    tCategory[["catalog.category"]]
    tStock[["inventory.stock"]]
    tOrder[["order-management.order"]]
    tPayment[["payment.payment"]]
  end

  search["Search & Discovery"]
  reviews["Reviews & Ratings"]
  recs["Recommendations"]
  later["Notifications, Support Assistant<br/><i>not built yet</i>"]

  catalog --> tProduct & tCategory
  inventory --> tStock
  orders --> tOrder
  payment --> tPayment

  tProduct --> search & reviews & recs
  tCategory --> search
  tStock --> search
  tOrder --> reviews & recs
  tPayment -.-> later
  tOrder -.-> later
```

## Paying a Checkout Session

Paying starts the checkout Saga on Temporal, with the session's ID as the workflow's ID
([ADR 0009](./adr/0009-sagas-on-temporal.md)). Checkout already holds the Cart's Stock in a
Reservation. The Saga compensates a failure by voiding an authorized Payment and cancelling the
Order. Clearing the Cart and ending the session come after the Order is paid. If either fails,
the failure is logged and the Order stays paid.

```mermaid
sequenceDiagram
  autonumber
  actor C as Customer
  participant CO as Checkout & Pricing
  participant S as Checkout Saga<br/>(Temporal)
  participant OM as Order Management
  participant P as Payment
  participant B as Payment gateway
  participant I as Inventory
  participant CA as Cart

  C->>CO: Pay
  CO->>S: start workflow (session ID)
  S->>OM: place Order
  S->>P: authorize Payment
  P->>B: authorize
  opt bank confirmation pending
    B-->>P: signed webhook settles it
    S->>P: await settlement (up to 10 min)
  end
  alt declined, or not settled
    S->>P: void Payment (if authorized)
    S->>OM: cancel Order
  else authorized
    S->>I: commit Reservation
    alt committed
      S->>OM: mark Order paid
      S->>CA: clear Cart
      S->>CO: end Checkout Session
    else Reservation lapsed or failed
      S->>P: void Payment
      S->>OM: cancel Order
    end
  end
  CO->>S: read outcome
  CO-->>C: Paid, Declined, Hold expired or Failed
```

## Not built yet

Fulfillment & Shipping, Returns & Warranty, Notifications and the AI Support Assistant are
planned in [`docs/roadmap.md`](./roadmap.md). The fulfillment and returns Sagas will run in
Orchestration beside the checkout Saga. Prometheus and Grafana scrape every service and are left
out of the diagrams.
