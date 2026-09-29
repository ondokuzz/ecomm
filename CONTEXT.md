# Ecomm Platform

A from-scratch e-commerce platform: customers browse a catalog, buy, track, and return items, with an AI assistant answering questions about their own orders.

## Language

**Customer**:
A person who browses, buys, and manages Orders on the platform. Keycloak holds a Customer's identity; the Customer ID is the access token's `sub`.
_Avoid_: User, Account, Buyer

**Staff**:
A back-office person with the `STAFF` role.
_Avoid_: Admin, Operator

**Product**:
A sellable item in the Catalog, identified by SKU, with category-specific attributes and one or more Variants.
_Avoid_: Item, SKU (SKU is an identifier, not the concept)

**Category**:
A grouping of Products that Customers browse by, such as `phones`, `laptops` or `audio`, named by a lowercase slug. A category exists as long as at least one Product is in it.
_Avoid_: Department, Collection

**Variant**:
A specific purchasable version of a Product (e.g. a color/storage combination), each with its own stock level, identified by its Variant ID. Until multi-Variant Products arrive, every Product has exactly one Variant, whose Variant ID is the Product's SKU.
_Avoid_: Option, Configuration

**Stock**:
How many units of a Variant are available to sell, counted per Variant ID and never negative. Inventory owns Stock. A checkout decrements the Stock of several Variants as one batch that applies whole or not at all.
_Avoid_: Inventory (that's the context, not the count)

**Price**:
What a Variant sells for, as `Money`: an amount in the currency's minor unit. Catalog owns Price. A Cart holds no authoritative price: whatever a Cart shows is a copy, and the Price that counts is always the one Catalog holds now.
_Avoid_: Cost (what the platform pays a supplier)

**Money**:
An amount as an integer in its currency's minor unit, with the currency's ISO 4217 code, such as `{"amountMinor": 79900, "currency": "EUR"}` for €799.00. Prices, Order totals and Payments are all Money, and amounts in different currencies are never added together.
_Avoid_: Amount (on its own), decimal or floating-point prices

**Cart**:
A Customer's in-progress, unconfirmed selection of Variants and quantities. Ephemeral — it is not an Order until checkout completes, and it lapses 7 days after the Customer last changed it. Each Customer has at most one Cart, and only they can see or change it.
_Avoid_: Basket, Bag

**Checkout**:
The step where a Customer confirms their Cart: its Variants are priced, their Stock is taken, payment is authorized, and an Order is placed.
_Avoid_: Purchase, Order placement

**Reservation**:
A temporary hold on a Variant's stock, created when checkout starts and either released on abandonment or converted to a permanent stock decrement on payment success. Not built yet: until Sprint 2, checkout decrements Stock directly.
_Avoid_: Lock, Hold

**Order**:
A Customer's confirmed intent to purchase one or more Variants, tracked through a lifecycle from placement to delivery or return. It holds one Order Line per Variant, all in one currency, and its total is the sum of its lines. It belongs to the Customer who placed it, and only they can see it.
_Avoid_: Purchase, Transaction

**Order reference**:
The short handle the Storefront shows an Order by, such as `#3F2A9C1B`: the first eight characters of its ID, upper-cased. Order IDs are random UUIDs, so it tells a Customer's Orders apart; the full ID stays on the Order page. Display only; no service looks an Order up by it.

**Order Line**:
One Variant, its quantity, and its unit price captured at checkout. The captured price stays with the Order even if the Variant's Price changes later.
_Avoid_: Item

**Order Status**:
The lifecycle stage of an Order: `Placed → Paid → Fulfilled → Shipped → Delivered`, with `Returned` and `Cancelled` as branches off that path. An Order can be `Cancelled` until it is Fulfilled and `Returned` once Delivered; both are final.
_Avoid_: State (Status is the domain term; state is a general programming concept)

**Payment**:
An Order's amount, as `Money`, taken through a payment gateway and recorded with the gateway's reference for it. A Payment starts `AUTHORIZED`: the gateway has approved the amount but not yet captured it. It belongs to the Customer who authorized it, and only they can see it. Capture and refund come later.
_Avoid_: Charge, Transaction

**Fulfillment**:
The physical pick/pack/ship process that turns a Paid Order into a Shipped Order.
_Avoid_: Shipping — that's the carrier hand-off specifically, one step inside Fulfillment

**RMA (Return Case)**:
A Customer-initiated request to return or exchange a Variant from a delivered Order, tracked through its own approve/inspect/resolve lifecycle, distinct from the original Order's lifecycle.
_Avoid_: Return, Refund — those are possible outcomes of an RMA, not the RMA itself

**Warranty Window**:
The time period, tied to a Product's category and manufacturer terms, during which an RMA can be opened for a given Variant.
_Avoid_: Return Period (Return Period is broader/retailer-set; Warranty Window is manufacturer-tied)

**Support Assistant**:
The AI-driven conversational agent that answers a Customer's questions about their own Order, Payment, or RMA, grounded in that Customer's real data.
_Avoid_: Chatbot, Bot

**Correlation ID**:
The ID that names one request as it passes from service to service, carried in the `X-Correlation-Id` header. It is taken from the caller when well-formed (at most 64 characters of `[A-Za-z0-9-]`) and generated otherwise. Every log line written while serving the request carries it, and so does every problem detail, where a Customer sees it as the support reference for an error. It is never used to decide anything.
_Avoid_: Request ID, Trace ID (tracing is a separate concern)
