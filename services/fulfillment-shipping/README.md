# Fulfillment & Shipping

Where an Order ships from, what shipping it costs, and its parcel from pick to delivery.
Fulfillment & Shipping holds the **Warehouses** as configuration, quotes **Shipping options** from a
**Carrier** behind its `ShippingProvider` port
([ADR 0005](../../docs/adr/0005-localization-compliance-abstracted.md)) for Checkout, and keeps
each paid Order's **Shipment**, and each RMA's **Replacement Shipment**: the fulfillment Saga
creates, dispatches and holds an Order's, the returns Saga creates and dispatches a replacement,
Staff pick and pack both, entering each unit's **Serial number**, and the Carrier reports its **Tracking events**
by **Carrier webhook** until it is delivered. Each change publishes a
`fulfillment-shipping.shipment` event through the outbox. It has its own Postgres database,
`fulfillment_shipping`, which `postgres-databases` creates on start when it is missing, and Flyway
migrates. Built from
[`platform/service-template`](../../platform/service-template/README.md), so its layout, security and
testing conventions apply here.

## Warehouses

Warehouses are configuration, under `ecomm.fulfillment-shipping.warehouses` in
[`application.yml`](./src/main/resources/application.yml), not data Staff edit. Each has an ID, a
name, an address with its country (ISO 3166-1 alpha-2), a **Return address** (name, street,
postcode, city and country) where Customers send an RMA's units back, and whether it is the
default:

| ID | Name | Country | Return address | Default |
|---|---|---|---|---|
| `WH-BER` | Berlin | DE | Ecomm Returns Berlin, Lagerstraße 3, 10115 Berlin | yes |
| `WH-AMS` | Amsterdam | NL | Ecomm Returns Amsterdam, Magazijnweg 3, 1011 AB Amsterdam | |

A destination is assigned the Warehouse in its country, or else the default: a Dutch address ships
from Amsterdam, a German or a French one from Berlin. Quotes and Shipments use this rule, so what
was quoted is what ships. The service doesn't start unless exactly one Warehouse is the default, no
ID is used twice, no two Warehouses share a country, and each has an ID, a name, a valid country
and a whole return address. Nor does it start when a Shipment names a Warehouse the configuration no longer has: it
fails listing those Shipments (the first 20) and their Warehouses, so that removing or renaming a
Warehouse can't strand them out of every queue. Stock stays one pool per Variant; Inventory knows
nothing of Warehouses.

## The mock carrier

The one `ShippingProvider` adapter, configured under `ecomm.fulfillment-shipping.mock-carrier`. It
is called `Mock Carrier`, ships to the EU's 27 countries, and prices each option from a table per
Currency, EUR only, by zone: **domestic** when the destination is in the origin Warehouse's
country, **EU** otherwise. The lines don't change the price.

| Option | Domestic | EU |
|---|---|---|
| `STANDARD` | €4.99, 2 days | €9.99, 4 days |
| `EXPRESS` | €9.99, 1 day | €19.99, 2 days |

A destination outside those countries, or a Currency without a table, gets no options. The service
doesn't start if an option lacks a price for either zone.

It books every Shipment it is handed, with a fresh `MC` and ten digits as its tracking number, and
answers a repeated idempotency key with its first booking (the latest 10,000 keys, until a
restart). It then sends a Carrier webhook for each Tracking event, `IN_TRANSIT`,
`OUT_FOR_DELIVERY` and `DELIVERED`, each its delay after booking (`tracking-delays`: 10, 20 and 30
seconds) and saying it happened then. It signs them with the `carrier-webhook-secret` it shares
with the service and hands them to a `CarrierWebhookDelivery`, which posts them to the service's own
`/webhooks/carrier` (or `webhook-url`); tests put a recorder in its place to deliver, repeat,
reorder or withhold them. A webhook still waiting when the service stops is lost.

## Shipments

A Shipment is one parcel: its kind, its Warehouse, Shipping address and Shipping option, its lines
(Variant, Product name, quantity, Serial tracking, and the Serial numbers once packed), its status
and status history, its Carrier and tracking number once booked, its Tracking events and
`deliveredAt`. Its kind is `ORDER`, an Order's units, or `REPLACEMENT`, units replacing an RMA's
faulty ones, which names its `rmaId` and the Order. Every Shipment from before replacements is an
`ORDER` one. An Order has one `ORDER` Shipment, and an RMA at most one replacement; a second of
either is a 409 `shipmentExists` with the existing one in `shipment`.

A replacement is created by the returns Saga with the Order Shipment's address and option, in the
Warehouse that serves the address, and lives as any Shipment does: picked and packed by Staff, with
new Serial numbers (a unit already shipped, the returned one included, is refused), dispatched by
Orchestration, delivered by Carrier webhooks. Nothing captures a Payment for it.

```
AWAITING_PICK → PICKED → PACKED → SHIPPED → DELIVERED
                          PACKED → ON_HOLD            (its capture was refused; final)
```

Any other move is a 409 `illegalTransition` that changes nothing. Each move appends a status
history entry, the status, when and by whom (a Staff member's ID, `ORCHESTRATION` or `CARRIER`),
and publishes the Shipment, in one transaction under the Shipment's row lock.

- **Product names and Serial tracking** come from a projection of Catalog's `catalog.product`
  events (group `fulfillment-shipping.products`), keeping the newest version of each Product and
  every Variant it has had. A Shipment's lines take them when it is created and keep them. Until
  the projection knows every Variant, creating is a 503 `productsNotKnownYet` with `Retry-After:
  5`, which the fulfillment Saga retries. `make fulfillment-rebuild` empties the projection and
  replays the topic.
- **Packing** takes one Serial number per unit of each serial-tracked line, by Variant ID. An IMEI
  is 15 digits whose last is a Luhn check digit; a serial number is 4 to 40 of `[A-Za-z0-9-]`, kept
  upper-cased. A malformed one, the wrong count, one entered twice in the Shipment, or one for a
  Variant that isn't tracked (or isn't shipped here) is a 422 naming each field, such as
  `serialNumbers.PHN-PIXEL-9[0]`. One another Shipment carries for the same Product is a 409
  `serialAlreadyShipped` with the `serialNumber`: a unit ships on one Shipment only, until returns
  (Sprint 6) say otherwise. Two Products may share a number, since manufacturers number their units
  alike, and a Shipment put on hold gives its numbers back, so a unit unpacked from it can be
  packed on another; the held Shipment still shows them.
- **Dispatching** books the Shipment with the Carrier, passing the request's `Idempotency-Key` on,
  so a retried dispatch books once.
- **Tracking events** are kept oldest first, however late each arrives. Only `DELIVERED` moves the
  Shipment on, from `SHIPPED` to `DELIVERED`, with `deliveredAt` the event's time and `CARRIER` in
  the history; a late `IN_TRANSIT` is recorded but never takes the status back.

## API

| Endpoint | Who | |
|---|---|---|
| `GET /warehouses` | Staff | Every Warehouse, in configuration order |
| `GET /warehouses/{id}` | Customer, Staff, `ORCHESTRATION`, `NOTIFICATIONS` | The Warehouse's name and return address; 404 for one not configured |
| `POST /shipping/quotes` | `CHECKOUT` | The Warehouse and Shipping options for a destination |
| `POST /shipments` | `ORCHESTRATION`, `Idempotency-Key` | 201: the Order's Shipment, or with `"kind": "REPLACEMENT"` and `"rmaId"` the RMA's replacement, awaiting pick; 409 `shipmentExists` with the existing `shipment`; 503 `productsNotKnownYet` |
| `POST /shipments/{id}/dispatch` | `ORCHESTRATION`, `Idempotency-Key` | `PACKED` → `SHIPPED`, booked with the Carrier |
| `POST /shipments/{id}/hold` | `ORCHESTRATION`, `Idempotency-Key` | `PACKED` → `ON_HOLD`, with `{"reason"}` (1 to 255 characters) |
| `POST /shipments/{id}/pick` | Staff | `AWAITING_PICK` → `PICKED` |
| `POST /shipments/{id}/pack` | Staff | `PICKED` → `PACKED`, with `{"serialNumbers": {"<variantId>": [...]}}` |
| `GET /shipments/{id}` | Staff, `ORCHESTRATION` | One Shipment |
| `GET /shipments?orderId=` | Customer (their own), Staff, `ORCHESTRATION` | The Order's `ORDER` Shipment; 404 for none, or another Customer's |
| `GET /shipments?rmaId=` | Customer (their own), Staff, `ORCHESTRATION` | The RMA's replacement; 404 for none, or another Customer's |
| `GET /shipments?status=&warehouseId=&kind=&page=&size=` | Staff, `ORCHESTRATION` | The fulfillment queue: a page (`items`, `page`, `size`, `total`), oldest first; `size` 1 to 100, 20 unless given |
| `POST /webhooks/carrier` | The Carrier, no token | A signed Carrier webhook |

The gateway routes `/api/fulfillment-shipping/`, except `/shipping/**`, which only Checkout calls,
and `POST /shipments`, `/dispatch` and `/hold`, which only the fulfillment Saga calls, over the
Compose network. It routes `POST /webhooks/carrier` without a token
([API gateway](../../platform/api-gateway/README.md)).

```json
GET /warehouses
[{"id": "WH-BER", "name": "Berlin",
  "address": {"line1": "Lagerstraße 1", "postcode": "10115", "city": "Berlin", "country": "DE"},
  "default": true},
 {"id": "WH-AMS", "name": "Amsterdam",
  "address": {"line1": "Magazijnweg 1", "postcode": "1011 AB", "city": "Amsterdam", "country": "NL"},
  "default": false}]
```

```json
POST /shipping/quotes
{"destination": {"recipientName": "Erika Mustermann", "line1": "Hauptstraße 5",
                 "postcode": "75001", "city": "Paris", "country": "FR"},
 "lines": [{"variantId": "PHN-PIXEL-9", "quantity": 1}],
 "currency": "EUR"}

200
{"warehouseId": "WH-BER",
 "options": [
   {"id": "STANDARD", "carrier": "Mock Carrier", "cost": {"amountMinor": 999, "currency": "EUR"}, "estimatedDays": 4},
   {"id": "EXPRESS", "carrier": "Mock Carrier", "cost": {"amountMinor": 1999, "currency": "EUR"}, "estimatedDays": 2}]}
```

- The destination is a Shipping address, which Checkout validates. Only its `country` decides the
  quote; its other fields are ignored.
- `lines` need at least one, each with a `variantId` and a positive integer `quantity`; `currency`
  is an ISO 4217 code.
- A missing or malformed field is a 400 problem detail naming it in `errors`, such as
  `[{"field": "destination.country", "message": "must be an ISO 3166-1 alpha-2 code, such as DE"}]`.
- No options, for a country or Currency the Carrier doesn't serve, is a 422 problem detail with
  `reason: shippingUnavailable`.
- Each quote logs a line: `Quoted 2 Shipping options to FR from WH-BER`.

```json
POST /shipments                                   (ORCHESTRATION, Idempotency-Key)
{"orderId": "5d2a9c1e-...", "customerId": "c0ffee00-...",
 "lines": [{"variantId": "PHN-PIXEL-9", "quantity": 1}],
 "shippingAddress": {"recipientName": "Erika Mustermann", "line1": "Hauptstraße 5", "line2": null,
                     "postcode": "10115", "city": "Berlin", "region": null, "country": "DE",
                     "phone": null},
 "shippingOption": {"id": "EXPRESS", "carrier": "Mock Carrier",
                    "cost": {"amountMinor": 999, "currency": "EUR"}, "estimatedDays": 1}}

201, Location: /shipments/0b8e6f2a-...
{"id": "0b8e6f2a-...", "orderId": "5d2a9c1e-...", "customerId": "c0ffee00-...",
 "warehouseId": "WH-BER", "status": "AWAITING_PICK",
 "shippingAddress": {...}, "shippingOption": {...},
 "lines": [{"variantId": "PHN-PIXEL-9", "productName": "Pixel 9", "quantity": 1,
            "serialTracking": "IMEI", "serialNumbers": []}],
 "carrier": null, "trackingNumber": null, "trackingEvents": [], "deliveredAt": null,
 "holdReason": null,
 "statusHistory": [{"status": "AWAITING_PICK", "at": "2026-10-13T09:00:00Z", "by": "ORCHESTRATION"}],
 "createdAt": "2026-10-13T09:00:00Z"}
```

A missing or malformed field of `POST /shipments` is a 400 naming it in `errors`, as for quotes;
`estimatedDays`, `line2`, `region` and `phone` may be left out. Every other answer is the Shipment
in the same shape.

```json
POST /webhooks/carrier                            (Carrier-Signature: sha256=<hex HMAC-SHA256 of the body>)
{"eventId": "trk_6f1c...", "trackingNumber": "MC0012345678", "status": "DELIVERED",
 "occurredAt": "2026-10-13T10:15:10Z"}
```

A Carrier webhook is answered 200 once recorded, and for a repeat of an event ID, which changes
nothing; 401 when the signature is missing or wrong; 400 when malformed; 404 when no Shipment has
the tracking number, so the Carrier sends it again.

[`http/fulfillment-shipping.http`](./http/fulfillment-shipping.http) exercises all of these against
the compose stack.

## Events

Each change publishes the Shipment to `fulfillment-shipping.shipment`, keyed by its ID
([schema](../../platform/event-schemas/schemas/fulfillment-shipping.shipment.json)): its version,
one more per change; the `change`, `CREATED`, the status moved to, or `TRACKED` for a Tracking
event that left the status as it was; and a snapshot with the kind, the RMA of a replacement,
the Order and Customer, Warehouse, status, lines with Serial numbers, Carrier, tracking number,
Tracking events, `deliveredAt`, `holdReason` and status history. The Shipping address stays out of
it. `kind` and `rmaId` came later: an event without `kind` is an `ORDER` Shipment's. Orchestration
wakes the fulfillment Saga with an `ORDER` Shipment's events and the returns Saga with a
replacement's; Returns & Warranty projects `ORDER` Shipments only.

## Run it

```sh
docker compose up -d --build fulfillment-shipping   # from the repo root; listens on localhost:8099
```

That host port bypasses the [API gateway](../../platform/api-gateway/README.md), for development
only. Staff reach `GET /warehouses` through the gateway, at `/api/fulfillment-shipping/warehouses`.

Or run Postgres and Keycloak in compose (`docker compose up -d postgres-databases keycloak`) and
start the service with `./gradlew :services:fulfillment-shipping:bootRun` on port 8080.

Its database password and the Carrier webhook secret are Compose secrets, mounted as the files
`spring.datasource.password` and `ecomm.fulfillment-shipping.carrier-webhooks.secret` (see the
root README's "Secrets"); `application.yml` has neither, and the service doesn't start without the
webhook secret. Outside Docker, give them as
`SPRING_DATASOURCE_PASSWORD="$(cat .secrets/ecomm/postgres-password)"` and
`ECOMM_FULFILLMENTSHIPPING_CARRIERWEBHOOKS_SECRET="$(cat .secrets/ecomm/carrier-webhook-secret)"`.

## Tests

- `WarehouseApiTest` and `ShippingQuoteApiTest` call the API over HTTP with
  [`FakeKeycloak`](../../platform/service-commons/src/testFixtures/java/com/ecomm/commons/security/FakeKeycloak.java)
  tokens, with the service's own configuration: the Warehouses for Staff only, a Warehouse's
  return address for a Customer, Staff, Orchestration and Notifications, assignment by
  country and default, domestic and EU prices, `shippingUnavailable` for an unserved country or
  Currency, quotes for `CHECKOUT` only, and each malformed field. Postgres runs in Testcontainers.
- The Shipment tests run the same way, against Kafka and the schema registry too
  (`EventBackbone`), with the mock carrier's webhooks recorded by `RecordedCarrierWebhooks` in
  place of HTTP delivery: `CreateShipmentApiTest` (Warehouse assignment, Product names, once per
  Order, idempotent replay, `productsNotKnownYet`, `ORCHESTRATION` only, malformed fields),
  `ShipmentTransitionApiTest` (every allowed and refused move, the history, the roles and keys),
  `PackShipmentApiTest` (IMEI and serial forms, counts, repeats, `serialAlreadyShipped`),
  `ReplacementShipmentApiTest` (once per RMA beside the Order's one Shipment, the kind and RMA
  checked, picked, packed with new Serial numbers, dispatched and delivered),
  `ReadShipmentsApiTest` (the Customer's own by Order and by RMA, Staff's queue by status,
  Warehouse and kind, paging),
  `CarrierWebhookApiTest` (delivery, a repeat recorded once, reordering, bad signatures),
  `ShipmentEventsApiTest` (an event per change with its kind and RMA, valid against the schema),
  `ProductProjectionApiTest` (the newest version wins) and `UnconfiguredWarehouseApiTest`
  (starting the service against a Shipment from a Warehouse no longer configured fails).
- `SerialNumberTest` and `ShipmentStatusTest` are pure JUnit for the Serial number forms and the
  state machine.
- `WarehouseConfigurationTest` starts the Warehouse configuration alone and checks the service
  refuses to start on none or two defaults, an ID twice, two Warehouses in one country, a
  country that isn't an ISO code, or a missing or partial return address.
- `MockCarrierTest` is pure JUnit for the mock carrier's zone pricing, a deliberate exception to
  testing at the HTTP seam.
- `ArchitectureTest` runs the shared `HexagonalRules`.
