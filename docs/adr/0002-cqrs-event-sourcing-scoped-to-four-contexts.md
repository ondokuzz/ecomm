# CQRS + event sourcing: four contexts, not fifteen

Order Management, Payment, Inventory, and Returns & Warranty each have a real audit-trail requirement and a lifecycle genuinely worth replaying — financial reconciliation, stock accuracy, warranty disputes. Every other context (Catalog, Cart, Notifications, and the rest) stays plain CRUD.

## Consequences

Forcing event sourcing onto ephemeral or low-audit-value data would buy complexity with no payoff. Scoping it to four contexts keeps the pattern's cost proportional to where it actually earns its keep.
