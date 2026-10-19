# Architecture

Five views of the running stack as of Sprint 5: how browsers reach the services, how the contexts
call each other, what flows over Kafka, how the checkout Saga pays for a Checkout Session, and how
the fulfillment Saga takes a paid Order to its Customer. [`GLOSSARY-MAP.md`](../GLOSSARY-MAP.md) is the
source of truth for the relationships. Update these diagrams when a relationship there changes.

## Requests

Browsers reach the services only through the API gateway, which rejects a missing or invalid token
at the edge ([ADR 0010](./adr/0010-api-gateway-authenticates-at-the-edge.md)). Every service still
checks the Keycloak-issued JWT itself. Each service shows the store it owns. The gateway routes
Payment's reads by Customers and Staff, and the payment gateway's signed webhook, but none of the
commands only Orchestration makes. The Carrier's signed webhook reaches Fulfillment & Shipping the
same way, without a token.

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
  carrier(["Carrier<br/>(mock)"])

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
    fulfillment["Fulfillment & Shipping<br/><i>Postgres</i>"]
    returns["Returns & Warranty<br/><i>Postgres</i>"]
  end

  browser --> storefront & admin
  browser -. sign in .-> keycloak
  storefront & admin -- "/api/" --> gateway
  gateway -. validates JWT .-> keycloak

  gateway --> catalog & search & reviews & recs & cart & checkout & promotions & inventory & orders & returns
  bank -- "signed webhook" --> gateway
  carrier -- "signed webhook" --> gateway
  gateway -- "reads, webhook" --> payment
  gateway -- "Shipments, webhook" --> fulfillment
```

## Calls between contexts

Contexts call each other's internal endpoints, which the gateway never routes, each with its own
client-credentials identity
([Identity ADR 0002](../services/identity-access/docs/adr/0002-service-identity-by-client-credentials.md)).
Checkout calls five contexts to start and change a Checkout Session, and starts the checkout Saga
to pay one. Orchestration starts the fulfillment Saga itself, from a paid Order's event. The Sagas'
workers then send each step's command.

```mermaid
flowchart TB
  checkout["Checkout & Pricing"]

  subgraph session["Starting and changing a Checkout Session"]
    direction LR
    cart["Cart"]
    catalog["Catalog"]
    promotions["Promotions"]
    inventory["Inventory"]
    quotes["Fulfillment & Shipping"]
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
  checkout -- quotes Shipping options --> quotes
  promotions -- "reads Categories,<br/>currencies" --> catalog

  checkout == "starts checkout Saga,<br/>reads outcome" ==> temporal
  temporal <--> orchestration
  orchestration -- "place, cancel, mark paid,<br/>fulfilled, shipped, delivered" --> orders
  orchestration -- "authorize, await settlement,<br/>void, find by Order, capture" --> payment
  payment -- "authorizes,<br/>captures" --> bank
  orchestration -- commit Reservation --> inventory
  orchestration -- clear Cart --> cart
  orchestration -- end session --> checkout
  orchestration -- "create, read,<br/>dispatch, hold Shipment" --> quotes
  quotes -- books Shipment --> carrier(["Carrier<br/>(mock)"])
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
  fulfillment["Fulfillment & Shipping"]
  returns["Returns & Warranty"]

  subgraph kafka["Kafka"]
    tProduct[["catalog.product"]]
    tCategory[["catalog.category"]]
    tStock[["inventory.stock"]]
    tOrder[["order-management.order"]]
    tPayment[["payment.payment"]]
    tShipment[["fulfillment-shipping.shipment"]]
    tRma[["returns-warranty.rma"]]
  end

  search["Search & Discovery"]
  reviews["Reviews & Ratings"]
  recs["Recommendations"]
  fulfillmentIn["Fulfillment & Shipping"]
  returnsIn["Returns & Warranty"]
  orchestration["Orchestration<br/><i>fulfillment Saga</i>"]
  later["Notifications, Support Assistant<br/><i>not built yet</i>"]

  catalog --> tProduct & tCategory
  inventory --> tStock
  orders --> tOrder
  payment --> tPayment
  fulfillment --> tShipment
  returns --> tRma

  tProduct --> search & reviews & recs & fulfillmentIn & returnsIn
  tCategory --> search
  tStock --> search
  tOrder --> reviews & recs & orchestration
  tShipment --> orchestration & returnsIn
  tPayment -.-> later
  tOrder -.-> later
  tRma -.-> later
```

Fulfillment & Shipping and Returns & Warranty appear twice, as a publisher on the left and a
consumer on the right, to keep the arrows one way. Orchestration consumes `order-management.order`
to start a paid Order's fulfillment Saga, and `fulfillment-shipping.shipment` to wake it; it
publishes nothing.

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

## Fulfilling a paid Order

A `Paid` Order with a Shipping address starts its fulfillment Saga, `fulfillment:<orderId>`, once
however often its event is delivered ([ADR 0009](./adr/0009-sagas-on-temporal.md)). Staff pick and
pack the Shipment in the Admin Console; the Saga waits for each change on
`fulfillment-shipping.shipment`, and reads the Shipment again now and then in case an event never
comes. The Payment is captured only once the Shipment is packed. Once it is captured nothing is
compensated: each later step is retried until it succeeds. A declined capture holds the Shipment
for good and leaves the Order `Paid`.

```mermaid
sequenceDiagram
  autonumber
  participant OM as Order Management
  participant S as Fulfillment Saga<br/>(Temporal)
  participant FS as Fulfillment & Shipping
  actor St as Staff
  participant P as Payment
  participant B as Payment gateway
  participant CR as Carrier

  OM-->>S: order-management.order: Paid, with a Shipping address
  S->>FS: create Shipment (from its Warehouse)
  St->>FS: pick, then pack (Serial numbers checked)
  FS-->>S: fulfillment-shipping.shipment: Packed
  S->>P: find the Order's Payment
  S->>P: capture Payment
  P->>B: capture
  alt capture declined, or still failing
    S->>FS: hold Shipment, with the reason
  else captured
    S->>OM: mark Order fulfilled
    S->>FS: dispatch Shipment
    FS->>CR: book it
    S->>OM: mark Order shipped
    CR-->>FS: signed webhooks: Tracking events, then delivered
    FS-->>S: fulfillment-shipping.shipment: Delivered
    alt delivered within 30 days
      S->>OM: mark Order delivered
    else no delivery by the deadline
      Note over S,OM: ends Delivery unconfirmed, the Order stays shipped
    end
  end
```

Returns & Warranty takes a Customer's RMA, a return within the Return Period or a warranty claim
within the Warranty Window, only against units its projection of `fulfillment-shipping.shipment`
shows were delivered. Staff approve or reject it in the Admin Console. No Saga drives an RMA yet.

## Not built yet

Notifications, the AI Support Assistant and the returns Saga are planned in
[`docs/roadmap.md`](./roadmap.md). The returns Saga will run in Orchestration beside the checkout
and fulfillment Sagas, refunding through Payment and restocking through Inventory. Prometheus and
Grafana scrape every service and are left out of the diagrams.
