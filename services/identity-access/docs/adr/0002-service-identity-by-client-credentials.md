# Service identity by client credentials

A service that calls another service's internal endpoints has its own identity in the `ecomm` realm: a confidential client using the client-credentials grant, whose service account holds a realm role named after the calling service (`CHECKOUT` first). Internal endpoints require that role, so a Customer's token gets 403 there even though it is a valid realm token. The token's `sub` is then the service account, not the Customer, so the Customer the call is about travels as a required `customerId` in the request. The receiving service checks it the same way it checks a token's `sub`: it becomes the owner of what is created, and a resource owned by anyone else is a 404.

## Considered Options

- **Forward the Customer's token everywhere.** This was the Sprint 1 starting point, and it was rejected because nothing tells the calling service apart from the Customer: a Customer could decrement stock, place an Order at prices of their choosing, or mark their own Order paid.
- **Enforce it at the API gateway only.** Rejected because it protects the edge, not the service: anything that reaches a service port inside the network still gets through.
- **Keycloak token exchange (RFC 8693),** which would carry the Customer as a signed claim. Deferred to #13: it is more machinery than Sprint 1 needs, and the Sagas coming in later sprints may call services long after any Customer token has expired.

## Consequences

- Services trust the calling service to name the Customer honestly. The role check limits that trust to the services that hold the role.
- Each calling service caches its token and fetches a new one when it has less than 60 seconds left. On a 401 it drops the cached token and retries once. That retry is safe even for non-idempotent calls, because the resource server rejects a request before any handler runs.
- Each client's secret is a fixed dev-only value in the realm file, like the `dev-cli` password grant.
- A new calling service (Fulfillment, in Sprint 5) gets its own client and role rather than a shared "internal" role, so no service can do everything every other service can.

## Revised 2026-10-21

A third service identity, `notifications` (#91), the first to call Keycloak's own admin API rather than another context. Notifications reads a Customer's email address and names there (`GET /admin/realms/ecomm/users/{id}`) behind its `CustomerDirectory` port, so it keeps no copy of identity data. Its service account holds only realm-management's `view-users` client role: no realm role, since it calls no context's internal endpoint, and nothing that could change a user. Its secret is generated like Checkout's and Orchestration's ([ADR 0011](../../../../docs/adr/0011-secrets-as-generated-files.md)). Realm sync, which adds a client an existing realm lacks, now grants a service account's client roles as well as its realm roles, so an upgraded stack's `notifications` client gets `view-users` too.
