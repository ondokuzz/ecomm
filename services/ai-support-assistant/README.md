# AI Support Assistant

The **Support Assistant** behind the Storefront's Help: a signed-in Customer asks about their own
Orders in their own words, and it answers from Order Management's reads, made when asked with the
Customer's own token ([ADR 0002](./docs/adr/0002-reads-with-the-customers-own-token.md)). It never
acts. Each **Conversation** is kept in the shared Redis under the service's own key prefix; the
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
with or without the `#`), each once: so a link always points to a real record of the Customer's,
never to one the model's text made up. The Storefront links an `ORDER` to its Order page.

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
| `find_orders` | Order Management's `GET /orders?page=0&size=5` | The Customer's latest Orders, newest first, at most 5: each one's ID, Order reference, Order Status, when it was placed and its total |
| `get_order` | Order Management's `GET /orders/{orderId}` | One Order: the same, its Order Lines (Variant, quantity, unit price) and its Order Status history (each Status and when) |

Each answers the context's JSON trimmed to those fields, amounts as `{"amount": "803.99",
"currency": "EUR"}`. Each call carries the Customer's own token and the request's Correlation ID.
Neither a miss nor a failure fails the turn: a 404 is a result saying the record wasn't found among
the Customer's, and a timeout (2 seconds to connect, 10 to answer: `connect-timeout`,
`read-timeout`), a 5xx, a refused connection or an answer that can't be read is a result saying it
can't be looked up now. The reply says so.

## Provider

The secret `anthropic-api-key`, mounted as a file, holds the Anthropic API key. `make secrets`
writes it empty when it is missing and never overwrites it. Empty, the **mock provider** answers,
and the service says so at start-up (`No Anthropic API key: the Support Assistant answers with the
mock provider`); the key is never logged. The Claude adapter isn't built yet (#118): with a key, the
service warns that it still answers with the mock.

The mock provider is deterministic. A message that mentions Orders (`order`, `where`, `status`,
`deliver…`, `parcel`, `package`, `shipp…`) has it call `find_orders`, then `get_order` for the latest
Order, and reply from a template over the result, naming the Order by its reference:

- "Your latest Order, #7F1C2A3B, is paid. It was placed on 3 November 2026 and paid on 3 November 2026."
- "You haven't placed any Orders yet."
- "I couldn't find that Order among yours." for a 404;
- "I couldn't look up your Orders just now. Please try again in a moment." for a failed lookup.

Any other message is answered with what it can help with, looking nothing up.

## Logs

Each turn logs one line, `replied` or, if it threw, `failed`, with the Correlation ID in the MDC
(and so in the ECS JSON under the `docker` profile), and never the Customer's words or the reply:

```
Turn in Conversation 5b0e… replied: tools [find_orders found, get_order found], 3 provider calls, tokens 0 in, 0 out, 0 cache read, in 41 ms
```

Each lookup also leaves the outbound call's line (`GET http://order-management:8080/orders/… -> 200
in 12 ms`), with the query string left out.

## Run it

```sh
docker compose up -d --build ai-support-assistant   # from the repo root; listens on localhost:8102
```

[`http/ai-support-assistant.http`](./http/ai-support-assistant.http) starts a Conversation as the
demo Customer, asks where their Order is, reads the transcript and ends it.
