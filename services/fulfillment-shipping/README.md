# Fulfillment & Shipping

Where an Order ships from, and what shipping it costs. Fulfillment & Shipping holds the
**Warehouses** as configuration, and quotes **Shipping options** from a **Carrier** behind its
`ShippingProvider` port ([ADR 0005](../../docs/adr/0005-localization-compliance-abstracted.md)),
for Checkout only. Shipments, from pick to delivery, come later in Sprint 5. It has its own Postgres
database, `fulfillment_shipping`, which `postgres-databases` creates on start when it is missing; it
holds no tables yet. Built from
[`platform/service-template`](../../platform/service-template/README.md), so its layout, security and
testing conventions apply here.

## Warehouses

Warehouses are configuration, under `ecomm.fulfillment-shipping.warehouses` in
[`application.yml`](./src/main/resources/application.yml), not data Staff edit. Each has an ID, a
name, an address with its country (ISO 3166-1 alpha-2), and whether it is the default:

| ID | Name | Country | Default |
|---|---|---|---|
| `WH-BER` | Berlin | DE | yes |
| `WH-AMS` | Amsterdam | NL | |

A destination is assigned the Warehouse in its country, or else the default: a Dutch address ships
from Amsterdam, a German or a French one from Berlin. Quotes use this rule, and Shipments will too,
so what was quoted is what ships. The service doesn't start unless exactly one Warehouse is the
default, no ID is used twice, no two Warehouses share a country, and each has an ID, a name and a
valid country. Stock stays one pool per Variant; Inventory knows nothing of Warehouses.

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

## API

| Endpoint | Who | |
|---|---|---|
| `GET /warehouses` | Staff | Every Warehouse, in configuration order |
| `POST /shipping/quotes` | `CHECKOUT` | The Warehouse and Shipping options for a destination |

The gateway routes `/api/fulfillment-shipping/`, except `/shipping/**`, which only Checkout calls
over the Compose network ([API gateway](../../platform/api-gateway/README.md)).

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
 "lines": [{"variantId": "PHN-PIXEL-9-OBSIDIAN-128", "quantity": 1}],
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

[`http/fulfillment-shipping.http`](./http/fulfillment-shipping.http) exercises both against the
compose stack.

## Run it

```sh
docker compose up -d --build fulfillment-shipping   # from the repo root; listens on localhost:8099
```

That host port bypasses the [API gateway](../../platform/api-gateway/README.md), for development
only. Staff reach `GET /warehouses` through the gateway, at `/api/fulfillment-shipping/warehouses`.

Or run Postgres and Keycloak in compose (`docker compose up -d postgres-databases keycloak`) and
start the service with `./gradlew :services:fulfillment-shipping:bootRun` on port 8080.

## Tests

- `WarehouseApiTest` and `ShippingQuoteApiTest` call the API over HTTP with
  [`FakeKeycloak`](../../platform/service-commons/src/testFixtures/java/com/ecomm/commons/security/FakeKeycloak.java)
  tokens, with the service's own configuration: the Warehouses for Staff only, assignment by
  country and default, domestic and EU prices, `shippingUnavailable` for an unserved country or
  Currency, quotes for `CHECKOUT` only, and each malformed field. Postgres runs in Testcontainers.
- `WarehouseConfigurationTest` starts the Warehouse configuration alone and checks the service
  refuses to start on none or two defaults, an ID twice, two Warehouses in one country, or a
  country that isn't an ISO code.
- `MockCarrierTest` is pure JUnit for the mock carrier's zone pricing, a deliberate exception to
  testing at the HTTP seam.
- `ArchitectureTest` runs the shared `HexagonalRules`.
