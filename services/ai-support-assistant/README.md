# AI Support Assistant

The **Support Assistant** behind the Storefront's Help: a signed-in Customer asks about their own
Orders, Shipments, Payments and RMAs, and about the Catalog's Products, in their own words, and it
answers from Order Management's, Fulfillment & Shipping's, Payment's, Returns & Warranty's and
Search & Discovery's reads, made when asked with the Customer's own token
([ADR 0002](./docs/adr/0002-reads-with-the-customers-own-token.md)). It never acts. Each **Conversation** is kept in the shared Redis under the service's own key prefix; the
service has no database, publishes no events and consumes no topics. Its model is behind the
`SupportModel` port ([ADR 0005](../../docs/adr/0005-localization-compliance-abstracted.md)), so far
answered by a [mock provider](#provider). It stays in Java ([ADR 0001](./docs/adr/0001-stays-in-java.md)),
and is built from [`platform/service-template`](../../platform/service-template/README.md), so its
layout, security and testing conventions apply here.

## API

For Customers only: a token without the `CUSTOMER` role gets 403, Staff's included, and no token
401. A Conversation is its Customer's alone: another Customer's, an unknown one, or one that ended
or lapsed, is a 404. The gateway routes it at `/api/support-assistant/`.

| Endpoint | |
|---|---|
| `POST /conversations` | Starts a Conversation: 201 with it, empty, and its URL in `Location` |
| `POST /conversations/{id}/messages` | Adds the Customer's message, `{"text": "Where is my Order?"}`, runs one turn, and answers the reply |
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
  out. `make seed-reset` flushes Redis, which ends every Conversation.

## A turn

A turn adds the Customer's message, then asks the model, handing it the fixed system prompt, the
tools it may ask for and the Conversation so far. While it answers with lookups, the turn runs them,
one at a time on the request's thread, adds the calls and their results to the Conversation, and
asks again; once it answers with text, that is the reply. The Conversation keeps every lookup and
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
mock provider`); the key is never logged. The Claude adapter isn't built yet (#118): with a key, the
service warns that it still answers with the mock.

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

## Logs

Each turn logs one line, `replied` or, if it threw, `failed`, with the Correlation ID in the MDC
(and so in the ECS JSON under the `docker` profile), and never the Customer's words or the reply:

```
Turn in Conversation 5b0e… replied: tools [find_orders found, get_order found, get_shipment found], 4 provider calls, tokens 0 in, 0 out, 0 cache read, in 41 ms
```

Each lookup also leaves the outbound call's line (`GET http://order-management:8080/orders/… -> 200
in 12 ms`), with the query string left out.

## Run it

```sh
docker compose up -d --build ai-support-assistant   # from the repo root; listens on localhost:8102
```

[`http/ai-support-assistant.http`](./http/ai-support-assistant.http) starts a Conversation as the
demo Customer, asks where their Order is, then about it by its Order reference, about a charge and
about a Refund, about an RMA by its RMA reference and whether their Order can still be returned,
about a Product's Stock in a Variant and its Warranty terms, and for Products that fit a need, reads
the transcript and ends it.
