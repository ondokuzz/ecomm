# Notifications

The messages Customers get about their Orders and RMAs. Notifications consumes other contexts'
integration events, records a **Notification** in its ledger for each message an event triggers on
each channel the Customer's **Notification preferences** allow, renders it from its **Message
templates** and sends it through the **Notification channel**'s `NotificationProvider`. It keeps its
ledger, the Notification preferences, what the mock SMS and push providers took, and a projection of
Product names in its own Postgres database, `notifications`, and publishes no events. It reads
Warehouses' Return addresses from Fulfillment & Shipping. Built from
[`platform/service-template`](../../platform/service-template/README.md), so its layout, security
and testing conventions apply here.

It sends the Order's confirmation, its Shipment shipped and delivered, a replacement shipped, a
Refund, and each step of an RMA.

## What is sent

| Message type | Trigger | Channels | Says |
|---|---|---|---|
| `ORDER_CONFIRMED` | An `order-management.order` event with `change` `STATUS_CHANGED` to `PAID` | `EMAIL` | The Order reference and when it was placed, each line's quantity, Product name (with its Variant's axis values) and total, the subtotal, Discounts, Shipping cost, Tax and total |
| `ORDER_SHIPPED` | A `fulfillment-shipping.shipment` event with `change` `SHIPPED` for an `ORDER` Shipment (or one with no `kind`) | `EMAIL`, `SMS`, `PUSH` | The Order reference, each line's quantity and Product name, the Carrier and tracking number |
| `ORDER_DELIVERED` | The same with `change` `DELIVERED` | `EMAIL`, `SMS`, `PUSH` | The Order reference, the day it was delivered (`deliveredAt`, or when the event occurred), and its lines |
| `REPLACEMENT_SHIPPED` | The same with `change` `SHIPPED` for a `REPLACEMENT` Shipment | `EMAIL`, `SMS`, `PUSH` | The RMA reference, the Order reference, its lines, the Carrier and tracking number; it is about the Order and the RMA |
| `PAYMENT_REFUNDED` | A `payment.payment` event with `change` `REFUNDED` | `EMAIL`, `SMS`, `PUSH` | The Refund's amount (the Payment's last approved `REFUND`), what it was for (its `reference`, such as an RMA reference) and the day, and the Order reference |
| `RMA_REQUESTED` | A `returns-warranty.rma` event with `change` `REQUESTED` | `EMAIL` | The RMA reference and kind (return or warranty claim), its units and Product name, and the Order reference; every RMA message says these, and is about the Order and the RMA |
| `RMA_APPROVED` | The same with `change` `APPROVED` | `EMAIL` | The Return address of its Warehouse (`warehouseId`), read from Fulfillment & Shipping, to write the RMA reference on the parcel, and the Send-back deadline (`sendBackBy`); with no Warehouse, or one Fulfillment & Shipping doesn't know, that its RMA page shows where |
| `RMA_REJECTED` | The same with `change` `REJECTED` | `EMAIL` | The rejection's reason: the note of its `REJECTED` step in `decisions` |
| `RMA_RECEIVED` | The same with `change` `RECEIVED` | `EMAIL` | That its units arrived, to be inspected |
| `RMA_CLOSED` | The same with `change` `CLOSED` | `EMAIL` | That it was closed with no Refund or replacement, and the Inspection's note (`inspection.note`) |
| `RMA_EXPIRED` | The same with `change` `EXPIRED` | `EMAIL` | That its units didn't arrive by the Send-back deadline |

Email is always sent; SMS and push only to a Customer whose Notification preferences turn them on,
and never an RMA's step. Nothing else triggers a message: an Order placed, cancelled, fulfilled,
shipped, delivered or returned, a Payment authorized, declined, voided or captured (a Capture is
covered by `ORDER_SHIPPED`), a Refund declined, any other Shipment change, a replacement delivered,
and an RMA inspected, put on hold or backfilled, `REFUNDED` (covered by `PAYMENT_REFUNDED`, which
names the RMA reference) or `EXCHANGED` (covered by `REPLACEMENT_SHIPPED`) record nothing.

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
  | `noRecipient` | permanent | Keycloak doesn't know the Customer, or they have no email address; for an SMS, no mobile number |
  | `optedOut` | permanent | An SMS or push whose channel the Customer's Notification preferences turned off after it was recorded |
  | `directoryUnavailable` | transient | Keycloak didn't answer, or refused Notifications' token |
  | `invalidAddress` | permanent | The address isn't one |
  | `smtp 5xx: …` | permanent | The mail server refused the recipient or the message |
  | `smtp 4xx: …`, `smtpUnavailable: …` | transient | The mail server asked to try later, or couldn't be reached |
  | `unexpectedError` | transient | Anything else; the stack trace is in the log |

- Every attempt logs a line, such as `ORDER_CONFIRMED EMAIL Notification 6c1e… for Customer
  0b6f…: attempt 1 SENT`.

## Notification preferences

Per Customer, which channels besides email their messages go on. Email is always on, since every
message is about their own Orders, Payments and RMAs. SMS is off until the Customer saves a mobile
number in E.164 form (`+4915112345678`; spaces are dropped) and turns it on; push is off until they
turn it on, and needs no device while its provider is a mock. A Customer who never saved theirs has
email only.

- **Recording** reads them in the consumer's transaction: an event's Message type is recorded on
  email, and on SMS and push when the type goes on them and the Customer turned them on.
- **Sending** reads them again: an SMS goes to the mobile number saved then, and an SMS or push
  whose channel was turned off since fails as `optedOut`.

## API

| Endpoint | Who | |
|---|---|---|
| `GET /notifications?orderId=` (or `rmaId=`, or `customerId=`) | Staff | The Notifications about an Order or RMA, or to a Customer |
| `GET /notifications/me` | Customer | Their own |
| `GET /templates` | Staff | Every Message type on each channel it goes on, rendered with its sample content |
| `POST /templates/{type}/{channel}/preview` | Staff | One of them; an optional body `{"locale": "en"}` |
| `GET /preferences/me` | Customer | Their Notification preferences |
| `PUT /preferences/me` | Customer | Saves them: `{"phoneNumber": "+4915112345678", "sms": true, "push": false}` |
| `GET /mock-providers/sms?customerId=` (or `push`) | Staff | What the mock SMS or push provider took for the Customer, newest first |

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

`recipient` is masked: for email the first character of the address's local part, then `***`; for
SMS the mobile number's first three characters and last two (`+49***78`); for push the Customer's
ID, where it went. `message` is `null` until it was rendered; `subject` is an email's subject or a
push's title, and `html` is `null` but for email. `outcome` is `SENT`, `TRANSIENT_FAILURE` or
`PERMANENT_FAILURE`. A preview reads `{"messageType", "channel", "locale", "subject", "html",
"text"}` alike; a Message type or channel there is none of, or a channel the type doesn't go on, is a
404. The Admin Console's **Messages** page shows every preview, its Order page the Order's
Notifications, and its RMA page the RMA's.

Notification preferences read
`{"email": true, "sms": true, "phoneNumber": "+4915112345678", "push": false, "updatedAt": "…"}`,
`updatedAt` `null` until first saved. Saving needs `sms` and `push` (a 400 without them); a
`phoneNumber` that isn't E.164, or `sms` on without one, is a 422 with `"reason":
"invalidPhoneNumber"`. The mock providers' reads are pages of `{"reference", "channel",
"customerId", "to", "title", "text", "sentAt"}`, `to` masked as above and `title` push's only; `email`
is a 404, since its provider isn't a mock.

[`http/notifications.http`](./http/notifications.http) exercises them against the compose stack.

## Message templates

A Message type has one template per channel and locale, each part its own JMustache file under
`src/main/resources/templates/<message-type>/<channel>/<locale>/`: for email, `subject.mustache`,
`html.mustache` and `text.mustache`; for SMS, `text.mustache`; for push, `title.mustache` and
`text.mustache`. The HTML part escapes what it is given; the others don't.

- **The model** comes from the triggering event, kept on the Notification, with Money and dates
  written for the locale (`€1,598.00`, `October 21, 2026`), and `customer.givenName` and
  `familyName` from the directory.
- **Locales.** A Customer's locale is Keycloak's `locale` attribute, or `en`. The lookup takes the
  locale, then its language, then `en`. Only `en` exists so far, so a market's language slots in by
  adding its directory (ADR 0005).
- **Checked at start-up.** Every Message type must have every part of each channel it goes on in
  `en`, and none for a channel it doesn't; every locale must be complete; every part must render its
  Message type's sample content, a name the model lacks included; and an SMS rendered so must fit 160
  characters. Otherwise the service refuses to start, naming every problem. The sample content is
  what Staff preview.
- **Every SMS fits 160 characters.** One whose content is unusually long, such as a very long
  Carrier name, is cut at 159 and an ellipsis when it is sent.

### Adding a Message type

1. Its content: a record in `domain/`, added to `MessageContent`'s `permits`.
2. Its constant in `MessageType`, with the channels it goes on and a sample of that content, which
   Staff preview and the start-up check renders. The compiler then asks for its case in
   `PostgresNotificationStore.contentOf`, which reads a stored Notification back.
3. Its templates, under `templates/<message-type>/`, every part of each of its channels; the
   start-up check names what is missing.
4. The event that triggers it: a use case recording it through `Ledger`, and, for a new topic, a
   listener whose group goes in [`infra/docker/consumer-groups`](../../infra/docker/consumer-groups)
   for its dead-letter and redrive topics (`infra/docker/stack-wiring-check.py` checks the group).
5. Nothing checks that the type is ever recorded, so a test at the HTTP seam that the event sends it (see [Tests](#tests)), its row in
   [What is sent](#what-is-sent), and the glossary's **Message type**. The Admin Console names a type
   from its constant, so it needs nothing.

## Channels and providers

Each Notification channel sends through a `NotificationProvider` port: `send(recipient, message)`
answers the provider's reference, or a transient or permanent failure (ADR 0005).

- **Email** goes over SMTP, `multipart/alternative` with the text body then the HTML one, from
  `Ecomm <orders@ecomm.local>`, its reference the `Message-ID`. In the compose stack it goes to
  **Mailpit**, which catches every message: read them on http://localhost:8025, or through its API
  (`GET /api/v1/messages`), as the Playwright suites do. A real relay is a matter of
  `spring.mail.*`.
- **SMS and push** go to mock providers: each sends nothing, but records what it would have sent,
  with its reference (`mock-sms-…`, `mock-push-…`), in a table of its own (`mock_sms_message`,
  `mock_push_message`), which Staff read at `/mock-providers/sms` and `/push`, and logs it, such as
  `Mock SMS mock-sms-… to +49***78: Ecomm: your Order #3F2A9C1B has shipped. …`. A real SMS gateway
  or push service, with device registration, replaces each as an adapter of its own (ADR 0005).

## Recipients

The `CustomerDirectory` port answers a Customer's email address, given name and family name, so
Notifications keeps no copy of identity data. Its adapter reads Keycloak's admin API
(`GET /admin/realms/ecomm/users/{id}`) with Notifications' own client-credentials token, from the
`notifications` client, whose service account holds realm-management's `view-users`
([Identity & Access ADR 0002](../identity-access/docs/adr/0002-service-identity-by-client-credentials.md)).
Answers, a Customer it doesn't know included, are kept for 10 minutes
(`ecomm.notifications.keycloak.cache-for`). Its secret is the Compose secret
`notifications-client-secret`, which realm sync sets on the client too
([ADR 0011](../../docs/adr/0011-secrets-as-generated-files.md)).

## The events it consumes

| Topic | Group | First start | Becomes |
|---|---|---|---|
| `order-management.order` | `notifications.orders` | latest offset | The Notifications an Order's change triggers |
| `fulfillment-shipping.shipment` | `notifications.shipments` | latest offset | The Notifications a Shipment's change triggers |
| `payment.payment` | `notifications.payments` | latest offset | The Notifications a Refund triggers |
| `returns-warranty.rma` | `notifications.rmas` | latest offset | The Notifications an RMA's step triggers |
| `catalog.product` | `notifications.products` | earliest offset | A row in `product` per SKU, its name and version, and in `product_variant` each Variant's axis values |

- **Nothing from before.** `notifications.orders`, `.shipments`, `.payments` and `.rmas` read from
  the latest offset on their first start, so nobody is told about an Order paid, a Shipment shipped,
  a Refund made or an RMA's step taken before Notifications existed. From then on each resumes where
  it left off, so what happened while Notifications was down is still told.
- **Product names.** Order events name only Variants, so the confirmation names each line from the
  projection of `catalog.product`, kept at each Product's newest version, by Variant ID while it
  hasn't caught up. `make notifications-rebuild` rebuilds it from the topic: it stops the service,
  resets `notifications.products` to the earliest offset, empties the projection and starts it
  again. The ledger stays, and so does `notifications.orders`'s offset, so nothing is sent again.
- An event that can't be read, or keeps failing, goes to its group's dead-letter topic, and `make
  dlt-redrive GROUP=<group>` redrives it once the cause is fixed
  ([service template](../../platform/service-template/README.md#consuming)). A redriven Order event
  records nothing it recorded before.

## Return addresses

`RMA_APPROVED` gives the Return address of the RMA's Warehouse. The `ReturnAddresses` port reads it
from Fulfillment & Shipping's `GET /warehouses/{id}`
(`ecomm.notifications.fulfillment-shipping-url`) with Notifications' own token from the
`notifications` client, whose service account holds the `NOTIFICATIONS` realm role for it. It is
read when the approval's event is consumed, before its transaction, so the email keeps the address
as it was then. A Warehouse it answers 404 for is sent without an address, with a warning in the
log. If Fulfillment & Shipping doesn't answer, the event is retried, then dead-lettered to
`notifications.rmas.dlt`; `make dlt-redrive GROUP=notifications.rmas` records it once it answers
again.

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

- **The HTTP seam and the topics** (`OrderConfirmedApiTest`, `ShipmentMessagesApiTest`,
  `RefundMessagesApiTest`, `RmaMessagesApiTest`, `PreferencesApiTest`, `LedgerApiTest`,
  `DispatchFailuresApiTest`, `TemplatesApiTest`): events are published as Order Management,
  Catalog, Fulfillment & Shipping, Payment and Returns & Warranty would, each checked against its
  schema first, and the ledger is read over HTTP. Email goes to a test double of the
  `NotificationProvider`; SMS and push go to the mock providers, read over HTTP as Staff read them;
  Keycloak's token endpoint and admin API, and Fulfillment & Shipping's Warehouse read, are
  WireMock. They cover each message's content, the same event twice recorded once, changes that
  trigger nothing, the Notification preferences (defaults, saving, an invalid number, SMS without
  one), an opted-in Customer's SMS and push, and their RMA messages by email alone, each RMA step
  (the approval's Return address read with Notifications' token, read again after Fulfillment &
  Shipping failed, and left out for a Warehouse it doesn't know), `optedOut`, reads by Staff and
  owner, retries then `FAILED`, permanent failures, `noRecipient`, Keycloak down then back, and the
  previews, every SMS within 160 characters and the approval's text as the Customer reads it.
  Postgres, Kafka and the registry run in Testcontainers.
- **Start-up** (`TemplatesStartUpTest`): an application of its own, whose templates lack the
  confirmation's text part, refuses to start, and so does one whose shipped SMS is over 160
  characters.
- **The outbound ports**: `EmailDeliveryTest` sends through the SMTP provider to a GreenMail
  container and reads the message over IMAP (subject, both bodies, recipient), fails an invalid
  address at once, and with the container paused, so the server stops answering, tries six times
  and then fails. GreenMail takes every recipient, so the mapping of SMTP reply codes (5xx
  permanent, 4xx transient) has no test against a server yet; `CustomerDirectoryTest`
  reads Keycloak in WireMock (the token, the lookup, the cache, unknown, no email, Keycloak down).
- `RetryPolicyTest` pins the backoff; `ArchitectureTest` runs the shared `HexagonalRules`.
- Each Spring test context is closed before the next starts (`spring.test.context.cache.maxSize=1`),
  so no earlier context's consumers or dispatcher take a later one's events or Notifications.
