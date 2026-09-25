# Ecomm Platform

A from-scratch e-commerce platform: customers browse a catalog, buy, track, and return items, with an AI assistant answering questions about their own orders.

## Language

**Customer**:
A person who browses, buys, and manages Orders on the platform.
_Avoid_: User, Account, Buyer

**Product**:
A sellable item in the Catalog, identified by SKU, with category-specific attributes and one or more Variants.
_Avoid_: Item, SKU (SKU is an identifier, not the concept)

**Variant**:
A specific purchasable version of a Product (e.g. a color/storage combination), each with its own stock level.
_Avoid_: Option, Configuration

**Cart**:
A Customer's in-progress, unconfirmed selection of Variants and quantities. Ephemeral — it is not an Order until checkout completes.
_Avoid_: Basket, Bag

**Reservation**:
A temporary hold on a Variant's stock, created when checkout starts and either released on abandonment or converted to a permanent stock decrement on payment success.
_Avoid_: Lock, Hold

**Order**:
A Customer's confirmed intent to purchase one or more Variants, tracked through a lifecycle from placement to delivery or return.
_Avoid_: Purchase, Transaction

**Order Status**:
The lifecycle stage of an Order: `Placed → Paid → Fulfilled → Shipped → Delivered`, with `Returned` and `Cancelled` as branches off that path.
_Avoid_: State (Status is the domain term; state is a general programming concept)

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
