# Notifications

The messages Customers get about their Orders. Notifications consumes other contexts' integration
events, records a **Notification** in its ledger for each message an event triggers, renders it from
its **Message templates** and sends it through the **Notification channel**'s `NotificationProvider`.
It keeps its ledger and a projection of Product names in its own Postgres database,
`notifications`, and publishes no events. Built from
[`platform/service-template`](../../platform/service-template/README.md), so its layout, security and
testing conventions apply here.

So far it sends one message, by email: `ORDER_CONFIRMED`, when an Order is paid. SMS, push,
Notification preferences, and the Shipment, Payment and RMA messages come with #83's next ticket.

## What is sent

| Message type | Trigger | Channels | Says |
|---|---|---|---|
| `ORDER_CONFIRMED` | An `order-management.order` event with `change` `STATUS_CHANGED` to `PAID` | `EMAIL` | The Order reference and when it was placed, each line's quantity, Product name (with its Variant's axis values) and total, the subtotal, Discounts, Shipping cost, Tax and total |

Nothing else triggers a message: an Order placed, cancelled, fulfilled, shipped, delivered or
returned records nothing yet.

## The Notification ledger

Each Notification is one message on one channel to one Customer, and keeps its Message type and
channel, its Customer, the triggering event's ID, the Order or RMA it is about, what it is rendered
from, its status, and once known its recipient's address, the rendered message, the provider's
reference and every attempt, with when it was made and what came of it.

- **Once per event.** (event ID, Message type, channel) is unique. The consumer records the
  Notification `PENDING` in one transaction, claiming that triple, so an event delivered again
  records, and sends, nothing more.
- **Sending.** A dispatcher looks for due `PENDING` Notifications every second, and sends each in a
  transaction of its own, holding its row (`FOR UPDATE SKIP LOCKED`) so no other instance sends it
  too. It reads the Customer from the `CustomerDirectory`, renders the message in their locale, and
  hands it to the channel's provider: `SENT` with the provider's reference, or a failure.
- **Retries.** A transient failure is tried again after 2, 4, 8, 16 and 32 minutes: 6 attempts over
  about an hour, then `FAILED` (`ecomm.notifications.dispatch.attempts` and `first-retry-after`). A
  permanent one is `FAILED` at once. The reasons:

  | Reason | Kind | When |
  |---|---|---|
  | `noRecipient` | permanent | Keycloak doesn't know the Customer, or they have no email address |
  | `directoryUnavailable` | transient | Keycloak didn't answer, or refused Notifications' token |
  | `invalidAddress` | permanent | The address isn't one |
  | `smtp 5xx: …` | permanent | The mail server refused the recipient or the message |
  | `smtp 4xx: …`, `smtpUnavailable: …` | transient | The mail server asked to try later, or couldn't be reached |
  | `unexpectedError` | transient | Anything else; the stack trace is in the log |

- Every attempt logs a line, such as `ORDER_CONFIRMED EMAIL Notification 6c1e… for Customer
  0b6f…: attempt 1 SENT`.

## API

| Endpoint | Who | |
|---|---|---|
| `GET /notifications?orderId=` (or `rmaId=`, or `customerId=`) | Staff | The Notifications about an Order or RMA, or to a Customer |
| `GET /notifications/me` | Customer | Their own |
| `GET /templates` | Staff | Every Message type on every channel, rendered with its sample content |
| `POST /templates/{type}/{channel}/preview` | Staff | One of them; an optional body `{"locale": "en"}` |

The lists are newest first, a page at a time (`page` from 0, `size` 1 to 100, 20 by default), as
`{"items": [...], "page": 0, "size": 20, "total": 1}`. Staff name exactly one of `orderId`, `rmaId`
and `customerId`; otherwise it is a 400. Each Notification reads:

```json
{"id": "6c1e…", "messageType": "ORDER_CONFIRMED", "channel": "EMAIL", "customerId": "0b6f…",
 "orderId": "3f2a9c1b-…", "rmaId": null, "recipient": "d***@ecomm.local", "status": "SENT",
 "message": {"subject": "Your Order #3F2A9C1B is confirmed", "html": "<!DOCTYPE html>…", "text": "Hello Demo,…"},
 "attempts": [{"number": 1, "at": "2026-10-21T19:45:02Z", "outcome": "SENT", "reason": null}],
 "createdAt": "2026-10-21T19:45:01Z", "sentAt": "2026-10-21T19:45:02Z", "nextAttemptAt": null}
```

`recipient` is masked: the first character of the address's local part, then `***`. `message` is
`null` until it was rendered, and `outcome` is `SENT`, `TRANSIENT_FAILURE` or `PERMANENT_FAILURE`.
A preview reads `{"messageType", "channel", "locale", "subject", "html", "text"}`; a Message type or
channel there is none of is a 404. The Admin Console's **Messages** page shows every preview.

[`http/notifications.http`](./http/notifications.http) exercises them against the compose stack.

## Message templates

A Message type has one template per channel and locale, each part its own JMustache file under
`src/main/resources/templates/<message-type>/<channel>/<locale>/`: for email, `subject.mustache`,
`html.mustache` and `text.mustache`. The HTML part escapes what it is given; the others don't.

- **The model** comes from the triggering event, kept on the Notification, with Money and dates
  written for the locale (`€1,598.00`, `October 21, 2026`), and `customer.givenName` and
  `familyName` from the directory.
- **Locales.** A Customer's locale is Keycloak's `locale` attribute, or `en`. The lookup takes the
  locale, then its language, then `en`. Only `en` exists so far, so a market's language slots in by
  adding its directory (ADR 0005).
- **Checked at start-up.** Every Message type must have every part of every channel in `en`, every
  locale must be complete, and every part must render its Message type's sample content, a name the
  model lacks included. Otherwise the service refuses to start, naming every problem. The sample
  content is what Staff preview.

## Channels and providers

Each Notification channel sends through a `NotificationProvider` port: `send(recipient, message)`
answers the provider's reference, or a transient or permanent failure (ADR 0005).

- **Email** goes over SMTP, `multipart/alternative` with the text body then the HTML one, from
  `Ecomm <orders@ecomm.local>`, its reference the `Message-ID`. In the compose stack it goes to
  **Mailpit**, which catches every message: read them on http://localhost:8025, or through its API
  (`GET /api/v1/messages`), as the Playwright suites do. A real relay is a matter of
  `spring.mail.*`.

## Recipients

The `CustomerDirectory` port answers a Customer's email address, given name and family name, so
Notifications keeps no copy of identity data. Its adapter reads Keycloak's admin API
(`GET /admin/realms/ecomm/users/{id}`) with Notifications' own client-credentials token, from the
`notifications` client, whose service account holds only realm-management's `view-users`
([Identity & Access ADR 0002](../identity-access/docs/adr/0002-service-identity-by-client-credentials.md)).
Answers, a Customer it doesn't know included, are kept for 10 minutes
(`ecomm.notifications.keycloak.cache-for`). Its secret is the Compose secret
`notifications-client-secret`, which realm sync sets on the client too
([ADR 0011](../../docs/adr/0011-secrets-as-generated-files.md)).

## The events it consumes

| Topic | Group | First start | Becomes |
|---|---|---|---|
| `order-management.order` | `notifications.orders` | latest offset | The Notifications an Order's change triggers |
| `catalog.product` | `notifications.products` | earliest offset | A row in `product` per SKU, its name and version, and in `product_variant` each Variant's axis values |

- **Nothing from before.** `notifications.orders` reads from the latest offset on its first start,
  so nobody is emailed about an Order paid before Notifications existed. From then on it resumes
  where it left off, so an Order paid while Notifications was down is still confirmed.
- **Product names.** Order events name only Variants, so the confirmation names each line from the
  projection of `catalog.product`, kept at each Product's newest version, by Variant ID while it
  hasn't caught up. `make notifications-rebuild` rebuilds it from the topic: it stops the service,
  resets `notifications.products` to the earliest offset, empties the projection and starts it
  again. The ledger stays, and so does `notifications.orders`'s offset, so nothing is sent again.
- An event that can't be read, or keeps failing, goes to its group's dead-letter topic, and `make
  dlt-redrive GROUP=<group>` redrives it once the cause is fixed
  ([service template](../../platform/service-template/README.md#consuming)). A redriven Order event
  records nothing it recorded before.

## Run it

```sh
docker compose up -d --build notifications   # from the repo root; listens on localhost:8101
```

That host port bypasses the [API gateway](../../platform/api-gateway/README.md), for development
only. Browsers reach the service through the gateway, at `/api/notifications/`. Mailpit's UI is on
http://localhost:8025.

Or run Postgres, Keycloak, Mailpit and the event backbone in compose (`docker compose up -d
postgres-databases keycloak-realm mailpit kafka-topics`) and start the service with
`./gradlew :services:notifications:bootRun` on port 8080, giving it its secrets:

```sh
SPRING_DATASOURCE_PASSWORD="$(cat .secrets/ecomm/postgres-password)" \
SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_NOTIFICATIONS_CLIENT_SECRET="$(cat .secrets/ecomm/notifications-client-secret)" \
./gradlew :services:notifications:bootRun
```

## Tests

- **The HTTP seam and the topics** (`OrderConfirmedApiTest`, `LedgerApiTest`,
  `DispatchFailuresApiTest`, `TemplatesApiTest`): events are published as Order Management and
  Catalog would, each checked against its schema first, and the ledger is read over HTTP. Email
  goes to a test double of the `NotificationProvider`, and Keycloak's token endpoint and admin API
  are WireMock. They cover the confirmation's content, the same event twice recorded once, changes
  that trigger nothing, reads by Staff and owner, retries then `FAILED`, permanent failures,
  `noRecipient`, Keycloak down then back, and the previews. Postgres, Kafka and the registry run in
  Testcontainers.
- **Start-up** (`TemplatesStartUpTest`): an application of its own, whose templates lack the
  confirmation's text part, refuses to start.
- **The outbound ports**: `EmailDeliveryTest` sends through the SMTP provider to a GreenMail
  container and reads the message over IMAP (subject, both bodies, recipient), fails an invalid
  address at once, and with the container paused, so the server stops answering, tries six times
  and then fails. GreenMail takes every recipient, so the mapping of SMTP reply codes (5xx
  permanent, 4xx transient) has no test against a server yet; `CustomerDirectoryTest`
  reads Keycloak in WireMock (the token, the lookup, the cache, unknown, no email, Keycloak down).
- `RetryPolicyTest` pins the backoff; `ArchitectureTest` runs the shared `HexagonalRules`.
- Each Spring test context is closed before the next starts (`spring.test.context.cache.maxSize=1`),
  so no earlier context's consumers or dispatcher take a later one's events or Notifications.
