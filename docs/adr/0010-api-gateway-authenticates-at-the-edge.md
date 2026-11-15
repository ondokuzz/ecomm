# The API gateway authenticates at the edge; services still authorize

All browser traffic enters through one API gateway ([`platform/api-gateway`](../../platform/api-gateway/README.md)), a Spring Cloud Gateway service in its servlet flavour. It does three things. It routes `/api/<service>/**` to each service. It authenticates at the edge: a routed request needs a valid `ecomm` token unless it is a public read, and without one it gets a 401 there, never reaching the service. And it hides internal endpoints, those only other services call, behind a 404. It forwards `Authorization` unchanged. Every service still validates that token and makes every role and ownership decision itself, as [Identity & Access ADR 0002](../../services/identity-access/docs/adr/0002-service-identity-by-client-credentials.md) requires: that ADR rejected enforcing only at the gateway, because anything that reaches a service port inside the network would get through. The gateway is an extra, outer check, not the only one.

## Considered Options

- **Authorize at the gateway too**, with each route's roles declared there. Rejected because it duplicates the services' rules in a second place that can drift from them, and a service must enforce them anyway.
- **A pass-through gateway that only routes.** Rejected because an invalid or missing token would then reach every service, and internal endpoints such as Inventory's stock decrement would be one path away from any browser.
- **Keep nginx as the edge,** with an `auth_request` to validate tokens. Rejected because nginx can't validate JWTs without a module or a sidecar, and the gateway is also where Correlation IDs, the access log and, later, rate limiting belong.

## Consequences

- Each token is validated twice, at the gateway and at the service. Both are local signature checks against Keycloak's cached keys, so the cost is small.
- Public reads and internal endpoints are declared in the gateway's route table as well as in each service. The gateway's table decides only what may pass; a service that forgets to guard an internal endpoint is still wrong, since the gateway doesn't guard traffic inside the network.
- The gateway is where a request's Correlation ID is usually born, and it writes the only access log.
- The services keep their dev host ports, which bypass the gateway. They are for development only.
- Rate limiting and circuit breakers arrive at the gateway in Sprint 7.

## Revised 2026-11-15

The route table gains a third kind of tokenless route beside public reads and webhooks: **signed links**, which a Customer opens from a message the service sent, without signing in, and which the service authenticates by the signed token the link carries (#127). The first is Notifications' Cart reminder unsubscribe link, `GET` and `POST /api/notifications/unsubscribe/cart-reminders?token=…`: `GET` answers a page asking to confirm, and `POST`, the page's button or a mail client's RFC 8058 one-click, turns cart reminders off. Like a webhook, it passes for its own methods and path only, and the service, not the gateway, decides: Notifications permits the path without a token and refuses a bad or expired one with a 400 page. The tokenless routes are now:

- public reads: Catalog's, Inventory's Stock, Search & Discovery's, Reviews & Ratings' and Recommendations' (the gateway's README lists them);
- webhooks: Payment's `POST /webhooks/gateway` and Fulfillment & Shipping's `POST /webhooks/carrier`;
- signed links: Notifications' `GET` and `POST /unsubscribe/cart-reminders`.
