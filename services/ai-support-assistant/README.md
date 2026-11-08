# AI Support Assistant

The **Support Assistant** behind the Storefront's Help: a signed-in Customer asks about their own
Orders, Shipments, Payments and RMAs, and about the Catalog's Products, in their own words, and it
answers from Order Management's, Fulfillment & Shipping's, Payment's, Returns & Warranty's and
Search & Discovery's reads, made when asked with the Customer's own token
([ADR 0002](./docs/adr/0002-reads-with-the-customers-own-token.md)). It never acts. Each **Conversation** is kept in the shared Redis under the service's own key prefix; the
service has no database, publishes no events and consumes no topics. Its model is behind the
`SupportModel` port ([ADR 0005](../../docs/adr/0005-localization-compliance-abstracted.md)),
answered by Claude when an API key is given, or else by a [mock provider](#provider)
([ADR 0003](./docs/adr/0003-claude-behind-a-port-with-a-mock-provider.md)). It stays in Java ([ADR 0001](./docs/adr/0001-stays-in-java.md)),
and is built from [`platform/service-template`](../../platform/service-template/README.md), so its
layout, security and testing conventions apply here.

## API

For Customers only: a token without the `CUSTOMER` role gets 403, Staff's included, and no token
401. A Conversation is its Customer's alone: another Customer's, an unknown one, or one that ended
or lapsed, is a 404. The gateway routes it at `/api/support-assistant/`.

| Endpoint | |
|---|---|
| `POST /conversations` | Starts a Conversation: 201 with it, empty, and its URL in `Location` |
| `POST /conversations/{id}/messages` | Adds the Customer's message, `{"text": "Where is my Order?"}`, runs one turn, and answers the reply. With an `Idempotency-Key`, a repeat answers the first reply without running the turn again. While another message's turn runs in the Conversation, a 409 `conversationBusy` |
| `GET /conversations/{id}` | The transcript: each message, oldest first |
| `DELETE /conversations/{id}` | Ends it: 204 |

A Conversation, and each message in it:

```json
{"id": "5b0e…",
 "messages": [
   {"role": "CUSTOMER", "text": "Where is my Order?", "references": [], "at": "2026-11-03T10:15:02.120Z"},
   {"role": "ASSISTANT",
    "text": "Your latest Order, #7F1C2A3B, is paid. It was placed on 3 November 2026 and paid on 3 November 2026.",
    "references": [{"kind": "ORDER", "id": "7f1c2a3b-…", "reference": "#7F1C2A3B", "label": "Order #7F1C2A3B"}],
    "at": "2026-11-03T10:15:02.480Z"}]}
```

Sending a message answers the reply alone, as the last message above. Its **references** are the
records the turn's lookups returned that the reply's text names by their reference (in either case,
with or without the `#`), or a Product by its name, each once: so a link always points to a real
record the lookups returned, never to one the model's text made up. The Storefront links an `ORDER` to its Order page, an
`RMA` (`{"kind": "RMA", "id": "3f2a9c1b-…", "reference": "RMA-3F2A9C1B", "label": "RMA-3F2A9C1B"}`)
to its RMA page, and a `PRODUCT` (`{"kind": "PRODUCT", "id": "PHN-PIXEL-9", "reference":
"PHN-PIXEL-9", "label": "Google Pixel 9"}`) to its Product page. A Product is named by its SKU or
by its name, in either case, but not by a name only part of a longer one the reply names: "Google
Pixel 9 Pro" links that Product, not the Google Pixel 9 as well.

- **A message** is 1 to 2,000 characters, not only whitespace; anything else, a body without `text`
  included, is a 400 with `"reason": "invalidMessage"`.
- **A Conversation** holds at most 20 of the Customer's messages. The 21st is a 409 with `"reason":
  "conversationFull"`, and the Customer starts a new one.
- **It lapses** 24 hours after its last message (`ecomm.support-assistant.conversation-lapses-after`),
  when Redis drops it. Ending it deletes it at once; the Storefront ends it when the Customer signs
  out. A turn saves its Conversation only while it is still kept, so one ended, or lapsed, while a
  turn runs stays gone: the turn's message answers 404, though its lookups ran and it counted against
  the Customer's limits. `make seed-reset` flushes Redis, which ends every Conversation.
- **An `Idempotency-Key`** on a message, such as a retry after a dropped connection, makes a repeat
  with the same key, in the same Conversation, answer the first reply with `Idempotent-Replayed:
  true`: no turn runs, nothing is added, and it isn't counted against the Customer's limits. A turn
  that failed kept nothing, so its repeat runs afresh. The key is kept with the message in the
  Conversation, not by the shared idempotency filter, which needs a database. The same key with
  another message is a 422 `idempotencyKeyReused`, and a key that isn't 1 to 255 printable ASCII
  characters a 400 `idempotencyKeyInvalid`. A repeat sent while the first's turn still runs is a 409
  `idempotencyKeyInFlight` rather than a second turn; once the first is answered, a repeat answers
  its reply, even while another message's turn runs.
- **One turn at a time** runs in a Conversation. Every message, with a key or without, claims its
  Conversation in Redis before its turn (`support-assistant:in-flight:<Conversation ID>`, holding a
  token of the turn's own and the message's `Idempotency-Key`, lapsing a minute after the turn's time
  should its instance stop mid-turn), and its turn reads the Conversation after the claim, so it
  starts from the last turn's save. Any other message sent meanwhile is a 409 `conversationBusy`,
  before the provider is asked, the message is added, or it counts against the Customer's limits;
  the Storefront's Help says the assistant is still answering the other message, keeping the text in
  the box. A turn releases its claim however it ends, and only while the claim is still its own, so
  a turn that outlived its claim can't release the next one's. Another of the Customer's
  Conversations isn't held.

## Limits and failures

Every limit is configuration under `ecomm.support-assistant` in `application.yml`, with these
defaults. Each refusal is a problem detail with its `reason`, and a refusal past a limit says in
`Retry-After` how many seconds to wait.

| Limit | Past it |
|---|---|
| A Customer sends at most 20 messages in any hour and 100 in any day, in whichever Conversations (`messages-per-hour`, `messages-per-day`), counted in Redis by the token's Customer | 429 `assistantRateLimited`, with `Retry-After` until the oldest message counted ages out, before the provider is asked anything. The mock provider is limited the same. A message refused as a 400 or 409 isn't counted |
| The service calls Claude at most 60 times in any minute, retries included, across every Customer and every instance (`provider-calls-per-minute`), counted in Redis | 503 `assistantBusy`, with `Retry-After` |
| A turn asks the provider at most 6 times, for at most 60 seconds (`provider-calls-per-turn`, `turn-time-limit`). Each call to Claude is given what is left of the 60 seconds when that is less than its own timeout | The reply "I'm sorry, that was too much to look up at once. Could you ask about one thing at a time?", with the lookups it made kept in the Conversation: when the turn reaches its 6th call, when its time is up between calls, or when a call is cut off by it or its retry would come after it |

A **circuit breaker** (Resilience4j) wraps each call to Claude (`breaker.*`), its retry included. A
429, a 529 overload, any other 5xx, a timeout or a connection failure is retried once, after the
wait Claude asks for in `retry-after-ms` or `retry-after`, or half a second; if the retry fails too,
or there is no time left for it, it counts against the breaker; it opens once half of
the last 20 calls failed, or 5 in a row, and while open answers at once without asking Claude. After
30 seconds it lets one call through: closed again if that call is answered, open again if it fails.
A call cut off by the turn's time doesn't count.
A 400 or 401 is a bug or a bad key: logged with the provider's error type (`The provider refused the
request as authentication_error (401): …`), never retried, and not counted. Each of these, the
breaker's refusal included, is a 503 `assistantUnavailable`, and the Conversation is left as it was
before the message. Neither the ceiling nor the breaker guards the mock provider, which has no key's
rate limit or budget to protect.

## A turn

A turn adds the Customer's message, then asks the model, handing it the fixed system prompt, the
tools it may ask for and the Conversation so far. While it answers with lookups, the turn runs them,
at once when it asks for several (each on a thread of its own, with the Customer's token and the
request's Correlation ID), adds the calls and their results to the Conversation, and asks again,
with the results together; once it answers with text, that is the reply. The Conversation keeps every lookup and
its result, not only the messages, so a follow-up ("and is it paid?") reads what was looked up
before. The system prompt names no Customer and no date, so it is the same on every turn.

The tools, each a read the Customer could make themselves:

| Tool | Reads | Answers |
|---|---|---|
| `find_orders` | Order Management's `GET /orders?page=0&size=5`, or with a `reference` `GET /orders?reference=…&page=0&size=5` | The Customer's latest Orders, or those with the Order reference, newest first, at most 5: each one's ID, Order reference, Order Status, when it was placed and its total |
| `get_order` | Order Management's `GET /orders/{orderId}` | One Order: the same, its Order Lines (Variant, quantity, unit price) and its Order Status history (each Status and when) |
| `get_shipment` | Fulfillment & Shipping's `GET /shipments?orderId=` | The Order's Shipment: the Order's ID and Order reference, status, Carrier and tracking number (null until booked), its Tracking events (each status and when), oldest first, and when it was delivered |
| `get_payments` | Payment's `GET /payments?orderId=` | The Order's ID and Order reference, and its Payments, newest first: each one's ID, status, amount and decline reason, and its Payment transactions, oldest first (kind, amount, outcome, decline reason, when, and what a Refund was for) |
| `get_payment_transaction_history` | Payment's `GET /payments/transactions?page=0&size=20`, with `from` and `to` if given | The Customer's Payment transaction history, newest first, at most 20: each transaction as above, with its Payment's ID and status, its Order's ID and its Order reference; and the `total` |
| `find_rmas` | Returns & Warranty's `GET /rmas?page=0&size=10`, with a `reference` and an `orderId` if given | The Customer's RMAs, all, those with the RMA reference, or those of the Order, newest first, at most 10: each one's ID, RMA reference, Order ID and Order reference, Product name, quantity, kind, status, when it was requested and its Send-back deadline (null until approved) |
| `get_rma` | Returns & Warranty's `GET /rmas/{rmaId}`, and while it is `APPROVED` Fulfillment & Shipping's `GET /warehouses/{warehouseId}` | One RMA: the same, the Customer's reason, when its window ends, its Refund amount, its Return address while approved (an `unavailable` error in its place if it can't be looked up now), its Inspection's outcome and note, its Refund's reference, and its RMA history (each status, when and its note), oldest first |
| `check_return_eligibility` | Returns & Warranty's `GET /rmas/eligibility?orderId=` | The Order's ID and Order reference, and each delivered line's Variant, Product name, when its Return Period ends, when its Warranty Window ends (null without Warranty terms) and its units not yet in an RMA; not found while it isn't delivered |
| `search_products` | Search & Discovery's `GET /search?q=…&facets=false&page=0&size=5` | The Products matching the `query`, best match first, at most 5: each one's SKU, name, lowest Variant Price (`priceFrom`, with `priceVaries`), whether any Variant is in Stock, and its attributes |
| `get_product` | Search & Discovery's `GET /products/{sku}` | One Product: its SKU, name, Category's name, attributes, Warranty terms (`months` and `manufacturer`; null without), and each Variant's ID, axis values, Price and whether it is in Stock, not how many; not found for a SKU the Catalog doesn't sell |

A `reference` is an Order reference, eight hex digits with or without the `#`, or for `find_rmas`
an RMA reference, with or without its `RMA-`, in either case; anything else, a `from` or `to`
that isn't an ISO 8601 instant, or a blank `query` or `sku`, is an error result that asks nothing.
Prices are the Catalog's, before any Campaign or Coupon; Search & Discovery's reads are anyone's,
and carry the Customer's token all the same. A reference no Order of the Customer's has answers no Orders, as another Customer's Order
with it does: the reply says it isn't among theirs. Each answers the context's JSON trimmed to those fields, amounts as `{"amount": "803.99",
"currency": "EUR"}`. Each call carries the Customer's own token and the request's Correlation ID.
Neither a miss nor a failure fails the turn: a 404 (another Customer's Order's Shipment, or one
the Order doesn't have yet) is a result saying the record wasn't found among the Customer's, and a timeout (2 seconds to connect, 10 to answer: `connect-timeout`,
`read-timeout`), a 5xx, a refused connection or an answer that can't be read is a result saying it
can't be looked up now. The reply says so.

## Provider

The secret `anthropic-api-key`, mounted as a file, holds the Anthropic API key. `make secrets`
writes it empty when it is missing and never overwrites it. Empty, the **mock provider** answers,
and the service says so at start-up (`No Anthropic API key: the Support Assistant answers with the
mock provider`); the key is never logged. With a key, **Claude** answers (`An Anthropic API key is
set: the Support Assistant answers with Claude (claude-opus-5-5)`).

### Claude

To give the stack a key, write it into the secret's file and recreate the service, from the repo
root:

```sh
printf '%s' 'sk-ant-…' > .secrets/ecomm/anthropic-api-key   # the Compose project's name, ecomm by default
docker compose up -d ai-support-assistant
```

Emptying the file and recreating the service the same way goes back to the mock. To run the mock
for a while without touching the key, such as for the Playwright suites, which expect its replies,
point `ANTHROPIC_API_KEY_FILE` at an empty file by its absolute path; leaving it unset goes back to
the key. Compose doesn't see a secret's file change as a change to the service, so recreate it:

```sh
: > /tmp/no-anthropic-api-key
ANTHROPIC_API_KEY_FILE=/tmp/no-anthropic-api-key docker compose up -d --force-recreate ai-support-assistant
docker compose up -d --force-recreate ai-support-assistant   # the key in .secrets again
```
 **With a key,
Customers' questions, and the data the tools return about them, go to Anthropic's API**, and each
message costs tokens; the logs hold neither the words nor the answers, only the tokens.

The adapter calls the Messages API through the Anthropic Java SDK
([ADR 0003](./docs/adr/0003-claude-behind-a-port-with-a-mock-provider.md)):

- the model `claude-opus-5-5` (`ecomm.support-assistant.anthropic-model`), with its default adaptive
  thinking and effort `low`, `max_tokens` 4,000, and no streaming;
- the tools with `strict: true` and `tool_choice` `auto`;
- a `cache_control` breakpoint after the system prompt, so the tools and the system prompt are read
  from the cache on every call after the first; the turn's log line shows the cache reads;
- the server-side refusal fallback (`fallbacks: "default"`).

Each of the Customer's messages reaches Claude prefixed by the service with the day it was sent and
the Storefront's address (`ecomm.support-assistant.storefront-url`, `http://localhost:8080`):
`[Sent on Thursday 5 November 2026. The Storefront is at http://localhost:8080.]`. Claude's answers
go back to it on later calls as it gave them, its thinking included. An answer that ends its turn is
the reply; one that asks for tools runs them; a refusal that stands is "I'm sorry, I can't help with
that. …", and an answer cut off at `max_tokens` "I'm sorry, my answer got too long to finish. …";
any other stop is a 503 `assistantUnavailable`, which doesn't count against the
[circuit breaker](#limits-and-failures). Each call times out after 30 seconds, or sooner when the
turn has less left, and is retried once (`anthropic-timeout`, `anthropic-max-retries`): by the
service, not the SDK, whose own retries are off, so each retry counts against the ceiling.

### The mock provider

The mock provider is deterministic. Each time it is asked, it reads the Customer's latest message
and what the turn has looked up so far, and asks for the next lookup or replies from a template over
the results, naming each Order by its reference and each Product by its name. The message's
words pick the path:

- **An RMA reference** (`RMA-3F2A9C1B`, in either case) has it call `find_rmas` with it, then
  `get_rma`, and answer with its status and next step: "Your RMA RMA-3F2A9C1B, a return of 1 Pixel 9
  on Order #7F1C2A3B, is approved. Please send it back by 21 November 2026, with RMA-3F2A9C1B on the
  parcel, to Ecomm Returns Berlin, Lagerweg 3, 12099 Berlin, DE." (or "to the Return address on its
  page" when it can't be read); requested and waiting for Staff; rejected, closed or on hold with
  its note ("Staff will look at it."); received, inspected with its outcome, refunded with its
  amount, exchanged, or expired with its Send-back deadline. None found is "I couldn't find an RMA
  RMA-DEADBEEF among yours. …".
- **About Products** (`stock`, `available`, `price`, `cost`, `how much`; `warrant…`, `guarantee…`;
  `looking for`, `recommend…`, `suggest…`, `search…`, `do you have`, `do you sell`, `show me`,
  `find me`, `alternative`), when the message names no Order reference and has none of `my`,
  `mine`, `I bought`, `I ordered`, `order`, `RMA`, `claim…` or a Payment's words, so "Is my phone
  still under warranty?" stays about returns. It calls `search_products` with the message's other
  words ("Is the Pixel 9 in 256 GB, black, in stock?" searches `Pixel 9 256 GB black`), and takes
  the Product whose name the message names most of, at least half its words, fewest left out
  (the Google Pixel 9, not the Google Pixel 9 Pro). Asked about Stock or its Warranty terms, it
  calls `get_product` for it and answers "The Google Pixel 9 is out of Stock in Obsidian, 256 GB at
  899.00 EUR." for the Variants whose axis values the message names most of, or each Variant, in
  Stock first; or "The Google Pixel 9 comes with Google's 24-month warranty, counted from the day
  it is delivered.", or "The JBL Flip 6 has no warranty." Otherwise, or naming none of them, it
  lists what it found: "I found 2 Products for "noise cancelling headphones": the Sony WH-1000XM5
  at 379.00 EUR, in Stock; and the Bose QuietComfort Ultra Headphones from 449.00 EUR, out of
  Stock." None found is "I couldn't find a Product for "unicorn". …", no words left to search for
  asks which Product, and a failed lookup is "I couldn't look up our Products just now. …".
- **About returns** (`return…`, `rma`, `warrant…`): asking whether (`can`, `could`, `still`,
  `eligible`, `under warranty`, `how long`, `until`, `want`, `request`, `start`), `check_return_eligibility`
  for the Order the message names, or else the latest: "On Order #7F1C2A3B, you can return the
  Pixel 9 until 6 December 2026, and claim under its warranty until 6 November 2028. To request a
  return or a warranty claim, open Order #7F1C2A3B's page.", a Return Period or Warranty Window
  that ended, as of the message, said so, and "Order #7F1C2A3B isn't delivered yet, …" for a 404.
  Otherwise `find_rmas`, of the Order named or all of them: one is explained as above, several
  listed with their status, and none is "You have no RMAs. To request a return, open the Order's
  page under My Orders."
- **An Order reference** (`#7F1C2A3B`, or eight hex digits with a digit among them, not the end of an
  RMA reference) has it call `find_orders` with that reference, and answer about that Order;
  otherwise it answers about the latest. None found is "I couldn't find an Order #DEADBEEF among
  yours. Please check the reference in your email."; several, a reply listing each one's Order
  Status, day and total that asks which one.
- **About Payments** (`charg…`, `twice`, `refund…`, `payment`, `bank`, `card`, `transaction`,
  `billed`): with an Order reference, `get_payments` for that Order; without one, the Payment
  transaction history. It tells each Order's transactions, oldest first: an Authorization "was
  only a hold on your card" once a Capture followed, or "is a hold on your card, not a charge"
  until then; a Capture "was the charge"; a declined Authorization took nothing, and a declined
  Capture charged nothing and held the Order. It opens with "You were charged once.", "You were
  charged for 2 Orders, #7F1C2A3B and #E5D40C11." or "Nothing has been charged yet." Asked about a
  Refund, it gives each one's amount, what it was for, its Order, and when it was approved ("Your
  bank may take a few days to show it.") or declined, with the gateway's reason.
- **About a Shipment** (`where`, `parcel`, `package`, `ship`, `shipp…`, `shipment`, `track…`,
  `carrier`, `deliver…`, `arriv…`): `get_order`, then, unless the Order is placed or cancelled,
  `get_shipment`, and its Order Status history followed by where the Shipment is: "It shipped with Mock Carrier, tracking
  number MC0123456789; its latest Tracking event: in transit on 4 November 2026.", "It was
  delivered on 6 November 2026 by …", "It hasn't shipped yet: it is waiting to be picked at the
  warehouse.", or for a 404 "It has no Shipment yet, so it isn't packed yet."
- **About an Order** (`order`, `status`): `get_order`, and its Order Status history: "Your latest
  Order, #7F1C2A3B, is paid. It was placed on 3 November 2026 and paid on 3 November 2026."
- "You haven't placed any Orders yet.", and "I couldn't look up your Orders just now. Please try
  again in a moment." (or "your Shipment", "your Payments", "your returns") for a failed lookup.

Any other message is answered with what it can help with, looking nothing up.

## Logs and metrics

Each turn logs one line, with how it ended: `replied`, `bounded` (at its 6 provider calls or 60
seconds), `unavailable`, `busy`, `gone` (its Conversation ended or lapsed while it ran, so it saved
nothing) or, for anything else it threw, `failed`; with the Correlation ID in the MDC
(and so in the ECS JSON under the `docker` profile), and never the Customer's words or the reply:

```
Turn in Conversation 5b0e… replied: tools [find_orders found, get_order found, get_shipment found], 4 provider calls, tokens 0 in, 0 out, 0 cache read, in 41 ms
```

Each lookup also leaves the outbound call's line (`GET http://order-management:8080/orders/… -> 200
in 12 ms`), with the query string left out.

On `/actuator/prometheus`, each at 0 from the start, so a dashboard's `increase()` counts the first:

| Metric | |
|---|---|
| `support_assistant_tokens_total{kind}` | The tokens the provider's answers took: `input`, `output` and `cache_read`. The mock's are 0 |
| `support_assistant_turn_duration_seconds{outcome}` | How long each message took to answer, by how its turn ended, as above; a histogram |
| `support_assistant_limit_refusals_total{limit}` | `messages_per_hour` and `messages_per_day` refusals, and `provider_calls_per_minute` turns refused at the ceiling |
| `support_assistant_provider_errors_total{kind}` | Failed calls to Claude: `rate_limited`, `overloaded`, `server_error`, `timeout` and `connection`, which count against the breaker, and `bad_request`, `unauthorized` and `other`, which don't |
| `support_assistant_provider_breaker_state{state}` | 1 for the state the breaker is in, `closed`, `open` or `half_open`, and 0 for the others |

The Services dashboard's Support Assistant row plots them.

## Run it

```sh
docker compose up -d --build ai-support-assistant   # from the repo root; listens on localhost:8102
```

[`http/ai-support-assistant.http`](./http/ai-support-assistant.http) starts a Conversation as the
demo Customer, asks where their Order is, then about it by its Order reference, about a charge and
about a Refund, about an RMA by its RMA reference and whether their Order can still be returned,
about a Product's Stock in a Variant and its Warranty terms, and for Products that fit a need, reads
sends a message with an `Idempotency-Key` twice, reads the transcript and ends it. 12 of its
messages count against the demo Customer's 20 an hour, so a second run within the hour reaches the
limit: its later messages are 429s.
