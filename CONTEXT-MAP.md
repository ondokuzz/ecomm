# Context Map

## Contexts

| Context | Lives at | Owns |
|---|---|---|
| Identity & Access | Keycloak realm `ecomm` (config in `services/identity-access`) | Customer and Staff authentication, roles and permissions |
| Catalog | `services/catalog` | Products, Variants, categories, specs, pricing |
| Search & Discovery | `services/search-discovery` | Faceted browse/filter — a read projection over Catalog |
| Reviews & Ratings | `services/reviews-ratings` | Verified-purchase reviews and ratings |
| Recommendations | `services/recommendations` | "Customers also bought," sourced from Order + Catalog events |
| Cart | `services/cart` | The ephemeral per-Customer Cart |
| Checkout & Pricing | `services/checkout-pricing` | Cart-to-Order orchestration, price/tax calculation |
| Promotions | `services/promotions` | Coupons, campaign rules |
| Payment | `services/payment` | Gateway integration, authorization/capture/refund — event-sourced |
| Order Management | `services/order-management` | Order lifecycle from placement to delivery — event-sourced |
| Inventory | `services/inventory` | Stock levels, Reservations, replenishment — event-sourced |
| Fulfillment & Shipping | `services/fulfillment-shipping` | Pick/pack/ship, carrier hand-off |
| Returns & Warranty | `services/returns-warranty` | RMA workflow, Warranty Window validation — event-sourced |
| Notifications | `services/notifications` | Email/SMS/push dispatch, triggered by domain events |
| AI Support Assistant | `services/ai-support-assistant` | The Support Assistant, grounded in real order/payment/returns data |

Not bounded contexts — UI layers over the above:

| App | Lives at |
|---|---|
| Storefront | `frontend/storefront` |
| Admin Console | `frontend/admin-console` |

## Relationships

- **Cart → Checkout & Pricing**: Checkout reads the Cart's contents to build an Order.
- **Checkout & Pricing → Order Management**: a successful checkout creates an Order.
- **Order Management → Inventory**: the fulfillment Saga dispatches Reservation commands; Inventory owns the Reservation lifecycle.
- **Order Management → Payment**: the fulfillment Saga dispatches payment-capture commands; Payment emits authorization/capture/refund events back.
- **Order Management → Fulfillment & Shipping**: `OrderPaid` is consumed to start pick/pack/ship.
- **Order Management → Notifications**: order-lifecycle events trigger customer notifications.
- **Returns & Warranty → Payment**: an approved RMA triggers a refund.
- **Returns & Warranty → Inventory**: an approved RMA triggers a restock.
- **Order Management, Payment, Returns & Warranty → AI Support Assistant**: the assistant queries each context's read APIs to ground its answers in real Customer data.
- **All contexts ↔ Kafka**: the shared event backbone — see [ADR 0006](./docs/adr/0006-kafka-as-single-event-backbone.md).

## Shared vocabulary

Term definitions currently live in one shared [`CONTEXT.md`](./CONTEXT.md) at the root rather than per-context files — the vocabulary hasn't diverged per context yet. A context earns its own `CONTEXT.md` (and this map gets updated to point to it) once its terms need a local definition that differs from the shared one.

## ADRs

System-wide decisions live in [`docs/adr/`](./docs/adr/). Context-specific decisions live inside the context they belong to (e.g. [`services/catalog/docs/adr/`](./services/catalog/docs/adr/)).
