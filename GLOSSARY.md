# Ecomm Platform

A from-scratch e-commerce platform: customers browse a catalog, buy, track, and return items, with an AI assistant answering questions about their own orders.

## Language

**Customer**:
A person who browses, buys, and manages Orders on the platform. Keycloak holds a Customer's identity; the Customer ID is the access token's `sub`.
_Avoid_: User, Account, Buyer

**Staff**:
A back-office person with the `STAFF` role. In the Admin Console, which turns away anyone without the role, Staff manage the Catalog, Coupons and Campaigns, look up every Customer's Orders with their Payments without changing them, read each Variant's Stock movements, and work two queues. In the fulfillment queue, Shipments by status and Warehouse, oldest first, Staff pick each Shipment and pack it with a Serial number per unit of each serial-tracked line; the fulfillment Saga does the rest, so Staff never capture a Payment, book a Carrier or hold a Shipment themselves. In the RMA approval queue, requested RMAs, oldest first, each is approved or rejected once by an RMA decision. A Staff member's ID is recorded on each Shipment status change and RMA decision they make. Customers can neither list, pick nor pack Shipments.
_Avoid_: Admin, Operator

**Storefront**:
The web app where Customers browse the Catalog, keep a Cart, check out and follow their Orders. Anyone may browse it; a Cart, checkout and Orders need a Customer's sign-in. It is a UI over the contexts, not one itself, and reaches them only through the API gateway.
_Avoid_: Shop, Site, Frontend (there are two)

**Admin Console**:
The web app where Staff manage the Catalog (Products with their Variants, Prices and Stock, and Categories with their Attribute definitions) and Promotions' Coupons and Campaigns, and read every Customer's Orders with their Order Status history, their Payments' Payment transactions and their Shipment, and each Variant's Stock movements. It holds Staff's two queues: the fulfillment queue, where Shipments are picked and packed, and the RMA approval queue. Only Staff get in; a Customer who signs in is told it is for Staff. Like the Storefront it is a UI over the contexts, and each service still checks the Staff role on every change.
_Avoid_: Admin panel, Back office, Dashboard

**Product**:
A sellable item in the Catalog, identified by SKU, with an optional description, category-specific attributes, one or more Variants, optional Warranty terms, and its Serial tracking.
_Avoid_: Item, SKU (SKU is an identifier, not the concept)

**Category**:
A Staff-managed grouping of Products that Customers browse by, such as `phones`, `laptops` or `audio`, named by a lowercase slug and a display name. It defines the attributes its Products carry through its Attribute definitions, so every Product in it is described the same way. Every Product belongs to a Category that exists, and a Category can't be deleted while it still has Products. Changing its definitions never rewrites existing Products; the new rules apply on each Product's next write.
_Avoid_: Department, Collection

**Attribute definition**:
One attribute a Category's Products carry: its name, its type (`TEXT`, `NUMBER`, `BOOLEAN`, or `ENUM` with its allowed values), whether it is required, and whether it is a Variant axis. A Product's attributes must satisfy its Category's non-axis definitions: every required one present, values of the right type, and nothing the Category doesn't define.
_Avoid_: Property, Field, Spec (a spec is how the Storefront shows an attribute)

**Variant axis**:
An Attribute definition that tells a Product's Variants apart rather than describing the Product, such as color or storage for `phones`. Each Variant has one value per axis of its Category.
_Avoid_: Option, Dimension

**Variant**:
A specific purchasable version of a Product (e.g. a color/storage combination), with its own Price, Stock and, optionally, images. A Product has one or more, told apart by their axis values: one value per Variant axis of its Category, no two Variants of a Product alike. Each is identified by its Variant ID, unique across the Catalog and never changed, and it lasts as long as its Product; a Product's first Variant usually has the Product's SKU as its ID. Carts, Orders and Stock name Variants, never Products.
_Avoid_: Option, Configuration

**Variant picker**:
How a Customer chooses a Variant on a Product's page in the Storefront: one group of choices per Variant axis, such as color and storage. Choosing a value leads to the Variant with that value and the chosen Variant's other values; a value no such Variant has can't be chosen. The chosen Variant's ID is in the page's URL, so it can be shared.
_Avoid_: Option selector, Configurator

**Facet**:
One way a Customer can narrow a search, with how many Products each choice would leave: the Categories, a Category's attributes by their values (or a `NUMBER`'s lowest and highest), the Price range in each Currency, and whether in Stock. Each is counted as if its own choice weren't made, so choosing one value still shows what its siblings would add; a choice that would leave nothing can't be made. Search & Discovery works them out with every search, and the Storefront's filter panel is built from them. Attribute Facets come only once a Category is chosen, since its Attribute definitions decide them.
_Avoid_: Filter (a Facet is what a filter is chosen from), Refinement, Aggregation

**Review**:
A Customer's rating of a Product from 1 to 5 stars, with an optional title of up to 120 characters and a body of up to 2,000. A Customer may write one once they have an Order for any of the Product's Variants that is `Paid` or later and not `Cancelled` or `Returned`, and at most one per Product; that is checked when they post it, so it stays if the Order is later cancelled. It names the Variant they bought, and its author by their given name and family name's initial, as they were when they posted it, never by Customer ID. Only its author may edit or delete it. Reviews & Ratings owns Reviews.
_Avoid_: Testimonial, Feedback, Comment

**Rating summary**:
A Product's Reviews in numbers: how many it has, their average rating to one decimal (a half rounded up; none without Reviews), and how many gave each star. Product cards and pages show it.
_Avoid_: Score, Rating (one Review's stars)

**Recommendation**:
A Product shown on another Product's page because Customers bought the two together: it was in Orders that also bought one of that Product's Variants and that count, `Paid` or later and not `Cancelled` or `Returned`. Recommendations are ranked by how many such Orders bought both, then by the most recently placed of them, then by SKU, and leave out the Product itself and Products removed from the Catalog. They are worked out when asked from what Recommendations has projected, so an Order cancelled after it was paid stops counting. The Storefront shows up to four as "Customers also bought". Recommendations owns them.
_Avoid_: Suggestion, Related product (which would be by likeness, not by what was bought), Cross-sell

**Stock**:
How many units of a Variant are available to sell, counted per Variant ID and never negative: its On-hand units less what active Reservations hold of them. Inventory owns Stock. Checkout takes it only through Reservations, which hold several Variants as one batch that applies whole or not at all. Staff remove a Variant's Stock when its Product leaves the Catalog, but not while Reservations hold some of it; Inventory then doesn't stock the Variant, and nothing can reserve it, until Staff set its On-hand count again. Its past Reservations stay.
_Avoid_: Inventory (that's the context, not the count)

**On-hand**:
How many units of a Variant Inventory physically has, whether or not Reservations hold them. Staff set it, never below what Reservations hold; committing a Reservation takes its units off it.
_Avoid_: Stock (Stock is what's left to sell), Physical stock

**Stock cache**:
Inventory's copy of each Variant's Stock in Redis, kept to spare Postgres the Storefront's Stock reads and Reservations that can't succeed. It is never the authority: Postgres decides every change, and each change rewrites the Variant's entry once it commits, a newer version never overwritten by an older one. An entry is trusted for 60 seconds at most, and no longer than the first Reservation holding some of the Variant has to run, so Stock an expiring Reservation gives back is available at once. Stock reads are served from it; a Reservation it already shows can't be met is refused without asking Postgres. Inventory works without it, from Postgres, while Redis is down.
_Avoid_: Stock snapshot, Stock replica

**Stock movement**:
One change to a Variant's On-hand units or to what Reservations hold of them, recorded for good: units received, reserved, released, committed, adjusted by Staff, or restocked from a return, with how many and when. A restock (`RESTOCKED`) is the returns Saga putting an accepted return's units back on hand, once per Idempotency key, and names the RMA reference it was for; the movements of a Reservation held for a replacement name the RMA that owns it, so Staff see which RMA a replacement's commit went to. Inventory owns them, and never changes or deletes one: a mistake is corrected by another. A Variant's On-hand is the sum of its movements, so the counter Inventory keeps beside them can always be checked against them. A Variant's ledger starts with an opening balance: an adjustment equal to the On-hand it had when Inventory began recording movements, and a reservation for each active Reservation then holding some of it.
_Avoid_: Stock change, Adjustment (one kind of movement), Transaction

**Price**:
What a Variant sells for, as `Money` in one of Catalog's Currencies; all of a Product's Variants are priced in the same one. Catalog owns Price. A Cart holds no authoritative price: whatever a Cart shows is a copy. The Price that counts is the one Catalog holds when a Checkout Session starts, which the session keeps for its lifetime.
_Avoid_: Cost (what the platform pays a supplier)

**Money**:
An amount as an integer in its Currency's Minor unit, with the Currency's ISO 4217 code, such as `{"amountMinor": 79900, "currency": "EUR"}` for €799.00. Prices, Order totals and Payments are all Money, and amounts in different Currencies are never added together. Staff type a Price as a decimal such as `799.00`; it becomes Money digit by digit, by its Currency's Minor unit, never through floating point.
_Avoid_: Amount (on its own), decimal or floating-point prices

**Currency**:
What Money is counted in: an ISO 4217 currency with a Minor unit, named by its code, such as `EUR`. Catalog lists the Currencies a Price can be in, with each one's Minor unit (`GET /currencies`), and that list is the one that counts: clients read and write Money by it rather than by their own currency data, which disagrees with ISO 4217 for some, such as HUF. Codes with no Minor unit, such as `XXX` (no currency) or `XAU` (gold), are no Currency to price in.
_Avoid_: Currency code (the code names a Currency)

**Minor unit**:
The smallest unit of a Currency, which Money counts in: the cent for EUR, the yen itself for JPY. Its number of digits says where a decimal's point goes: 2 for EUR, 0 for JPY, 3 for BHD, so `amountMinor` 79900 is €799.00. ISO 4217 sets it, as Catalog's Currencies give it; an amount read with another Currency's digits is off by a power of ten.
_Avoid_: Cents (one Currency's Minor unit), Decimals, Precision

**Cart**:
A Customer's in-progress, unconfirmed selection of Variants and quantities. Ephemeral — it is not an Order until checkout completes, and it lapses 7 days after the Customer last changed it. Each Customer has at most one Cart, and only they can see or change it.
_Avoid_: Basket, Bag

**Checkout**:
How a Customer turns their Cart into an Order, in two steps. Starting it opens a Checkout Session: the Cart's Variants are priced and their Stock reserved, which Checkout does itself. The Customer then gives the session a Shipping address and chooses a Shipping option. Paying the session starts the checkout Saga, a Payment attempt, which places the Order at those Prices, authorizes its Payment, commits the Reservation, marks the Order paid, clears the Cart and ends the session. Checkout answers with the attempt's outcome, or that it is still being confirmed.
_Avoid_: Purchase, Order placement

**Checkout Session**:
A Customer's Cart held for checkout for 15 minutes: its lines at the Prices captured when it started, with each line's Category; its Discounts, from the running Campaigns and the one Coupon applied to it, if any; their tax; a Reservation of their Stock; and, once given, its Shipping address, the Shipping options quoted for it, and the one chosen with its Shipping cost. Its total is the subtotal, less every Discount, plus the tax, plus the Shipping cost. Paying it honours those Prices and that Shipping cost even if Catalog or the Carrier has changed them since; paying one that has expired does nothing, and paying one without a Shipping address and a chosen Shipping option is refused (`shippingIncomplete`). A Customer has at most one: starting checkout again replaces it and releases its Reservation. Checkout owns it.
_Avoid_: Checkout (the whole two-step process), Hold, Basket

**Reservation**:
A temporary, all-or-nothing hold on the Stock of several Variants for one owner. A Customer's is created when a Checkout Session starts and lasts 2 minutes longer than the session. An RMA's, owned by `rma:<rmaId>`, holds the replacement unit of a warranty claim: only the returns Saga makes one, an RMA has at most one that isn't released, and asking again answers it, unless it expired uncommitted, when it is released and replaced; too little Stock (`insufficientStock`) turns the replacement into a Refund. It is `ACTIVE` until it is committed, which takes its units off On-hand for good on payment success, or released, which gives them back on abandonment. It holds Stock only while `ACTIVE` and before its `expiresAt`: from that moment it holds nothing, even before a sweep marks it `RELEASED`, and it can no longer be committed. Only Checkout, for its Customer, can release it early, and only Orchestration can commit it: the checkout Saga a Customer's, the returns Saga an RMA's.
_Avoid_: Lock, Hold

**Order**:
A Customer's confirmed intent to purchase one or more Variants, tracked through a lifecycle from placement to delivery or return. It holds one Order Line per Variant, its Discounts in the order they applied, its tax, and its Shipping address and Shipping option with the Shipping cost, all in one currency. Its total is the sum of its lines, less every Discount, plus the tax, plus the Shipping cost; Discounts never touch the Shipping cost, and the total is never negative. An Order placed before Sprint 5 has no Shipping address or option. That total is what its Payment is authorized for. It belongs to the Customer who placed it: only they and Staff can see it, and Staff can't change it.
_Avoid_: Purchase, Transaction

**Coupon**:
A code a Customer enters at checkout for a Discount. It takes a percentage (1 to 100) or a fixed amount of Money off, and applies only while it is active and within its validity window, to a subtotal of at least its optional minimum, in the same currency as its amount and minimum. Its code is matched whatever the case. Staff manage Coupons, which Promotions owns; a Checkout Session holds at most one, applied after its Campaigns on what they leave, and an Order records the code of the Coupon behind its Discount. When one doesn't apply, the reason is one of `unknown`, `inactive`, `notYetValid`, `expired`, `belowMinimum` or `currencyMismatch`.
_Avoid_: Voucher, Promo code

**Campaign**:
A Discount Staff set up to apply to every qualifying Checkout Session by itself, with no code, from when the session starts. Like a Coupon, it takes a percentage (1 to 100) or a fixed amount of Money off, has a validity window and an optional minimum subtotal, and is switched on or off. It may be limited to some of Catalog's Categories, then discounting only those lines and measuring its minimum on what they come to before any Discount, applying to every line when it names none. Each has a unique integer priority, and Campaigns apply lowest first, each on what its lines still come to after the ones before. At any moment it is running, scheduled, over or off. Staff manage Campaigns, which Promotions owns.
_Avoid_: Sale, Promotion (the context), Automatic discount

**Discount**:
An amount taken off a Checkout Session, and so off the Order paid from it, by a Coupon or a Campaign: recorded on both with what gave it, the Coupon's code or the Campaign's ID and name. A session gets every running Campaign's it qualifies for, then its Coupon's, last; Promotions works them all out together, when the session starts and whenever its Coupon changes. Each is worked out on what its lines still come to after the ones before, while a minimum is measured on what they came to before any, a percentage rounded down to the currency's minor unit, so together they are never more than the subtotal: they can bring the total to zero, but not below. Tax is worked out on the subtotal less every Discount.
_Avoid_: Rebate, Markdown

**Tax**:
What a market's tax rules add to an Order's lines, worked out by Checkout's `TaxCalculator` (zero for now, ADR 0005) and recorded on the Order, even when zero.
_Avoid_: VAT (one kind of it)

**Order reference**:
The short handle the Storefront shows an Order by, such as `#3F2A9C1B`: the first eight characters of its ID, upper-cased. Order IDs are random UUIDs, so it tells a Customer's Orders apart; the full ID stays on the Order page. Staff find the Order a Customer quotes by it, as a prefix of the ID (Order Management's `idPrefix`); it isn't stored anywhere.

**Order Line**:
One Variant, its quantity, and its unit price captured when the Checkout Session started. The captured price stays with the Order even if the Variant's Price changes later.
_Avoid_: Item

**Order Status**:
The lifecycle stage of an Order: `Placed → Paid → Fulfilled → Shipped → Delivered`, with `Returned` and `Cancelled` as branches off that path. An Order can be `Cancelled` until it is Fulfilled and `Returned` once Delivered; both are final.
_Avoid_: State (Status is the domain term; state is a general programming concept)

**Order Status history**:
Every Order Status an Order has been in, oldest first, starting with its placement: for each, when the Order reached it and which caller moved it there: `ORCHESTRATION` for a change a Saga made (checkout's Paid or Cancelled, and Fulfillment's Fulfilled, Shipped and Delivered), or `CHECKOUT` for one Checkout made itself, as every change was before the Saga. Order Management appends an entry with every change, in the same transaction, and never changes or deletes one; the Order's current Order Status is its last entry. An Order placed before histories were kept (Sprints 1–2) has its placement and, if it had moved on, its Order Status then, marked _backfilled_ and timed at its placement, since the real time was never recorded.
_Avoid_: Status log, audit trail, timeline (the timeline is how the Storefront draws it)

**Payment**:
An Order's amount, as `Money`, for one Customer, taken through a payment gateway with a Payment method and kept as its Payment transactions. Its status is worked out from them, and never set any other way: `PENDING` while its authorization awaits the gateway's answer; `AUTHORIZED` once the gateway has approved the amount, while it is neither captured nor voided, and still after a capture the gateway declined; `DECLINED`, for good, with the gateway's decline reason (such as `insufficient_funds`); `VOIDED`, for good, once it is released by a Void; `CAPTURED` ("Charged" to the Customer and Staff) once a Capture has charged the amount; `PARTIALLY_REFUNDED` ("Partly refunded") once Refunds have given back part of what was captured; or `REFUNDED`, for good, once they have given back all of it. A declined Refund leaves it as it was. A gateway that fails to answer records no Payment at all. It belongs to the Customer who authorized it: only they, Staff and Orchestration (naming the Customer) can see it, and Staff can't change it.
_Avoid_: Charge, Transaction

**Payment transaction**:
One interaction with the payment gateway on a Payment, recorded for good: an `AUTHORIZATION`, a `VOID`, a `CAPTURE` or a `REFUND`. It records its amount, its outcome (`APPROVED`, `DECLINED` or `PENDING`), the gateway's reference and decline reason, the gateway's event ID when a webhook recorded it, when it happened, for a `REFUND` what it was for (its reference, such as an RMA reference), and whether it was backfilled: reconstructed from a Payment recorded before Sprint 4, timed when it was reconstructed. Payment owns them, and never changes or deletes one. A Payment's status is worked out from its transactions, which are what it is reconciled against the gateway by.
_Avoid_: Charge, Transaction (on its own), Payment event

**Void**:
Releasing an authorized or pending Payment's authorization through the gateway before anything is captured, so the Customer's money is no longer held. It records a `VOID` Payment transaction and leaves the Payment `VOIDED`. Voiding a voided Payment again changes nothing; a declined one has nothing to release, and can't be voided; a captured or refunded one has been charged, so only a Refund returns its money (`alreadyCaptured`). The checkout Saga voids a Payment to compensate for an Order it can't complete.
_Avoid_: Cancel (an Order is cancelled; a Payment is voided), Refund (which returns captured money)

**Capture**:
Charging an authorized Payment's full amount through the gateway, which the fulfillment Saga does once the Order's Shipment is packed, so the Customer is charged only for what ships and never more than was authorized. It records a `CAPTURE` Payment transaction. Approved, it leaves the Payment `CAPTURED`; declined, it records the gateway's reason (such as `authorization_expired`) and leaves the Payment `AUTHORIZED`, and the Saga holds the Shipment rather than retry. Capturing a captured Payment again changes nothing, even once it has been refunded; a pending, declined or voided one can't be captured. Partial capture is out of scope.
_Avoid_: Charge (on its own), Settlement (a Gateway webhook settles an authorization), Payment (the whole record)

**Refund**:
Giving back part or all of what a Capture charged, through the gateway, for a reason the Refund names, such as an RMA reference; the returns Saga refunds a Payment once a return is accepted. It records a `REFUND` Payment transaction, in the captured amount's Currency, and the approved ones never come to more than the amount captured: more than is left is refused before the gateway is asked (`refundExceedsCaptured`). Approved, it leaves the Payment `PARTIALLY_REFUNDED`, or `REFUNDED` once the Refunds reach the amount captured; declined, it records the gateway's reason (such as `refund_window_closed`) and leaves the Payment as it was. Only a captured or partly refunded Payment can be refunded (`paymentNotRefundable`), and only by Orchestration. A Refund sent twice with the same Idempotency key refunds once.
_Avoid_: Void (which releases an authorization before anything is captured), Return (an RMA's kind, which a Refund may resolve), Chargeback (which the Customer's bank starts, not the store)

**Payment method**:
How a Customer pays: an opaque token the payment gateway issued for their card, which the checkout Saga passes on to Payment and nothing else reads. It travels in the Saga's input, and so in its history, which is safe because it is never a card number. The mock gateway takes test tokens: `tok_approve` authorizes; `tok_decline` and `tok_insufficient_funds` decline; `tok_gateway_error` fails to answer; `tok_async_approve` and `tok_async_decline` stand for a card the Customer's bank confirms later, answered as pending and settled a few seconds later by a Gateway webhook, approved or declined; `tok_capture_decline` authorizes, but every Capture of it is declined as `authorization_expired`; `tok_refund_decline` authorizes and is captured, but every Refund of it is declined as `refund_window_closed`.
_Avoid_: Card (a Payment method stands for a card, but is never its number)

**Gateway webhook**:
The payment gateway's notice to Payment that an authorization it left pending has settled, approved or declined, once the Customer's bank has confirmed it. It names the authorization by the gateway's reference, carries the gateway's own event ID, and is signed with a secret the two share, since the gateway calls from outside with no token. Payment records each event ID once: the first settles a pending Payment with an `AUTHORIZATION` Payment transaction carrying the event ID; a repeat, or one for a Payment already settled or voided, changes nothing.
_Avoid_: Callback, notification (on their own), Payment event (what Payment publishes)

**Payment attempt**:
One try at paying a Checkout Session: one run of the checkout Saga, named by the session's ID. It is `PROCESSING` while the Saga runs, and ends `PAID`; `DECLINED`, with the gateway's reason; `HOLD_EXPIRED`, when the Reservation stopped holding the Stock before it could be committed, so the Payment was voided and the Order cancelled; or `FAILED`, when a step failed for good and what came before it was undone. A declined or failed attempt leaves the session to be paid again, as a new attempt with its own Order; paying while one runs joins it, and a session already paid is never paid again. The latest attempt can be read after the session has ended, for as long as Temporal keeps its history.
_Avoid_: Payment (what Payment records for an Order), Transaction, Checkout (the whole two-step process)

**Warranty terms**:
A Product's manufacturer's warranty: how many months it lasts, from 1 to 120, and which manufacturer gives it, such as "24 months, manufacturer's warranty (Samsung)". They apply to all of the Product's Variants. A Product may have none, which the Storefront shows as "No manufacturer's warranty"; a Product stored before they existed has none. Staff set them in the Admin Console.
_Avoid_: Guarantee, Warranty period (the Warranty Window is the dates a warranty claim is open, worked out from these)

**Serial tracking**:
How each unit of a Product's Variants is told apart when it ships: `NONE`, `SERIAL` (a serial number) or `IMEI` (a phone's IMEI). It applies to all of the Product's Variants, and is `NONE` unless Staff set it. Seeded phones use `IMEI`, and laptops and audio `SERIAL`.
_Avoid_: Serialization, Serialized inventory

**Fulfillment**:
The process that takes a Paid Order to the Customer, run as the fulfillment Saga, `fulfillment:<orderId>`: create its Shipment, await Staff's picking and packing, Capture the Payment, mark the Order Fulfilled, dispatch the Shipment to its Carrier, mark the Order Shipped, await the Carrier's delivery and mark the Order Delivered. A Capture the gateway declines puts the Shipment on hold instead, and the Order stays Paid. An Order paid without a Shipping address, before Sprint 5, has no Fulfillment.
_Avoid_: Shipping — that's the carrier hand-off specifically, one step inside Fulfillment

**Warehouse**:
A place Orders ship from, such as `WH-BER` in Berlin: an ID, a name and an address with its country. Warehouses are configuration in Fulfillment & Shipping, not data Staff edit. Exactly one is the default, and a country has at most one. An Order ships from the Warehouse in its destination's country, or else from the default, and a Shipping quote uses the same rule, so what was quoted is what ships. Stock is one pool per Variant, not held per Warehouse.
_Avoid_: Depot, Fulfillment center, Location

**Carrier**:
The company that takes a parcel from a Warehouse to the Customer: it quotes Shipping options, books a Shipment and reports its progress. Fulfillment & Shipping reaches it through its `ShippingProvider` port (ADR 0005); only the mock carrier exists, which ships within the EU.
_Avoid_: Courier, Shipping provider (the port, not the company)

**Shipping option**:
A way a Carrier offers to ship an Order from its Warehouse to a destination, as a quote answers it: an ID such as `STANDARD` or `EXPRESS`, the Carrier, its cost as Money and its estimated days. The mock carrier prices each option per Currency by zone: domestic when the destination is in the Warehouse's country, EU otherwise. A destination or Currency the Carrier doesn't serve gets none. Only Checkout asks for them.
_Avoid_: Shipping method, Delivery option, Rate

**Shipping address**:
Where a Customer's Order should be sent: the recipient's name, one or two lines, a postcode, a city and an optional region, an ISO 3166-1 alpha-2 country, and an optional phone. Checkout validates it through its `AddressValidator` port (ADR 0005) as it is entered, naming each field's problem, such as a postcode in the wrong format for its country, or refusing a country the store doesn't ship to (`undeliverableCountry`); a valid one comes back normalised: trimmed, its postcode in its country's usual form. A Checkout Session holds one, and the Order keeps it.
_Avoid_: Delivery address, Destination (that is what a Shipping quote calls it)

**Shipping cost**:
The cost of the Shipping option chosen for a Checkout Session, as it was quoted for its Shipping address. The session keeps it for its lifetime, as it keeps Prices, until the address changes, which quotes again. It is added to the total after the Discounts, which never apply to it, and its tax is zero, as all tax is so far. The Order keeps it with its Shipping option.
_Avoid_: Delivery fee, Postage

**Shipment**:
One Order's parcel, in Fulfillment & Shipping: its Warehouse, its Shipping address and Shipping option, its lines (Variant, Product name, quantity, Serial tracking, and the Serial numbers once packed), its Carrier and tracking number once booked, and its Tracking events. One Order has one Shipment. Its status moves `AWAITING_PICK → PICKED → PACKED → SHIPPED → DELIVERED`, or `PACKED → ON_HOLD` when its Payment can't be captured, which is final. The fulfillment Saga creates, dispatches and holds it, Staff pick and pack it, and the Carrier's webhook delivers it. Each move appends to its status history: the status, when, and by whom (a Staff member's ID, `ORCHESTRATION` or `CARRIER`).
_Avoid_: Parcel, Package, Consignment, Delivery

**Serial number**:
What tells one unit of a serial-tracked Variant from another, entered by Staff for each unit as they pack a Shipment. An IMEI is the kind for phones: 15 digits, the last a Luhn check digit. Any other is 4 to 40 letters, digits and hyphens, kept upper-cased. A Serial number ships on one Shipment only, and is unique for its Product, not across Products; a Shipment put on hold gives its numbers back for another to pack. Returns & Warranty checks an RMA's units against them.
_Avoid_: Serial (on its own), Unit ID, IMEI (one kind of Serial number, not another name for it)

**Tracking event**:
Something the Carrier reports of a Shipment it has booked: `IN_TRANSIT`, `OUT_FOR_DELIVERY` or `DELIVERED`, with when it happened and the Carrier's own event ID. A Shipment keeps every one, oldest first, however late each arrives; only `DELIVERED` moves it on, to `DELIVERED`, so its status never goes back.
_Avoid_: Tracking update, Scan, Shipment event (what Fulfillment & Shipping publishes)

**Carrier webhook**:
The Carrier's notice of a Tracking event, posted to Fulfillment & Shipping's `POST /webhooks/carrier`. It names the Shipment by its tracking number and is signed with a secret the two share, since the Carrier calls from outside with no token. Each event ID is recorded once; a repeat changes nothing. The mock carrier sends one for each Tracking event after a set delay (10, 20 and 30 seconds after booking on the stack).
_Avoid_: Callback, notification (on their own), Gateway webhook (the payment gateway's)

**RMA (Return Case)**:
A Customer's request about units of one Variant from their delivered Order, of one of two kinds: a return (`RETURN`), within the Return Period, or a warranty claim (`WARRANTY_CLAIM`), within the Warranty Window. It names how many units, the Serial number of each where the Product has Serial tracking, and the Customer's reason. It is refused if those units weren't delivered, or another of the Customer's RMAs that isn't rejected holds them. It keeps the window it was checked against. Its status is `REQUESTED`, then `APPROVED` or `REJECTED` by an RMA decision; receiving, inspecting and resolving an approved RMA come in Sprint 6. Its own lifecycle is apart from its Order's. It is shown by its RMA reference, such as `RMA-3F2A9C1B`: `RMA-` and the first eight characters of its ID, upper-cased, like an Order reference. Returns & Warranty owns RMAs.
_Avoid_: Return, Refund — those are possible outcomes of an RMA, not the RMA itself

**RMA decision**:
Staff approving or rejecting a `REQUESTED` RMA: who decided, when, and a note, which an approval may leave out and a rejection must give as its reason. An RMA is decided once; its decision history only ever grows.
_Avoid_: Review, Resolution (what follows an approval in Sprint 6)

**Return Period**:
How long after its Shipment is delivered a Customer may request a return of a Variant: 14 days, one for the store, whatever the Product. A return requested after it ends is refused with the moment it ended.
_Avoid_: Return window, Cooling-off period, Warranty Window (the manufacturer's, for warranty claims)

**Warranty Window**:
The time in which a Customer may make a warranty claim on a Variant: from its Shipment's delivery for its Product's Warranty terms' months, counted on the calendar, so a delivery on 31 January with one month's warranty ends on the last day of February. A Product without Warranty terms has none. An RMA keeps the Warranty Window it was checked against, with the terms it came from, so a later change to the Product's Warranty terms doesn't change it.
_Avoid_: Return Period (the store's, for returns), Warranty terms (what it is worked out from)

**Support Assistant**:
The AI-driven conversational agent that answers a Customer's questions about their own Order, Payment, or RMA, grounded in that Customer's real data.
_Avoid_: Chatbot, Bot

**Correlation ID**:
The ID that names one request as it passes from service to service, carried in the `X-Correlation-Id` header. It is taken from the caller when well-formed (at most 64 characters of `[A-Za-z0-9-]`) and generated otherwise. Every log line written while serving the request carries it, and so does every problem detail, where a Customer sees it as the support reference for an error. An Integration event a request causes carries it in the same header, so the consumers' log lines carry it too. It is never used to decide anything.
_Avoid_: Request ID, Trace ID (tracing is a separate concern)

**Integration event**:
What one context publishes to Kafka so that others can learn a change it made: a snapshot of one aggregate, such as an Order or a Variant's Stock, carrying the state its consumers need, its version and why it was published. Each topic carries one kind, and its JSON Schema in the repo defines it. It is published in the same transaction as the change, through the Outbox, and delivered at least once and not strictly in order, so a consumer applies it only if its version is newer than the one it holds. It describes a change that has happened; it never asks a context to do something.
_Avoid_: Domain event (one that stays inside a context), Message, Command

**Outbox**:
Where a context records an Integration event, in the same transaction as the change it describes, until Kafka has it. The Postgres-backed contexts use Spring Modulith's event publication registry; Catalog writes an outbox document in the same Couchbase transaction, which a relay in Catalog sends. Either way, an event whose send failed stays there, visible, and is sent again.
_Avoid_: Event store (no context is event-sourced)

**Dead-letter topic**:
Where a consumer group's Integration events go when it can't handle them: `<consumer group>.dlt`, one per group. An event the group's listener keeps failing on goes there after its retries, and one it can't read at all goes at once, so that the partition moves on to later events. Each keeps the event as it was read, Correlation ID included, with where it came from (topic, partition, offset, group), the error and when. Once the cause is fixed, its dead events are redriven: copied to `<consumer group>.redrive`, which the group's listeners also consume, and applied by version like any other event. One that fails again is dead-lettered again.
_Avoid_: Error queue, Poison queue, Parking lot

**Saga**:
A business process that spans contexts, such as checkout, run as a Temporal workflow in Orchestration. Each step calls one context's command API, with an Idempotency key unless repeating the command changes nothing anyway; a step that fails is retried, and a business refusal, such as a declined Payment, is answered by compensating, undoing what the earlier steps did through those contexts' own commands, such as cancelling the Order and voiding the Payment. Compensations are retried until they succeed. Once a step can't be undone, such as committing Stock, the steps after it are retried rather than compensated: marking the Order paid until it succeeds, clearing the Cart and ending the session for up to an hour. The fulfillment Saga likewise compensates nothing once the Payment is captured, and answers a declined Capture by holding the Shipment. A Saga's steps are commands over HTTP, but the fulfillment Saga is started by an Order's event and woken by its Shipment's events, as it waits hours or days on Staff and the Carrier. Its history in Temporal records the process, not the Order or Payment it touched, which their contexts keep.
_Avoid_: Workflow (Temporal's mechanism, not the business process), Process manager

**Orchestration**:
The service that runs every Saga as Temporal's worker, owning no domain data. It calls other contexts with its own identity, the `orchestration` client whose service account holds the `ORCHESTRATION` role, never with a Customer's token. Each command it calls names the Customer as `customerId` in its body, and the gateway routes none of them: placing an Order and changing its Order Status, authorizing, capturing and voiding a Payment, committing a Reservation, clearing a Customer's Cart, and ending a Checkout Session; and Fulfillment & Shipping's commands, creating a Shipment for the Order and its Customer, dispatching it and holding it. It reads a Payment, and an Order's Payments, as the Customer's own reads do, naming the Customer as `customerId` in the query. It consumes Order events (group `orchestration.orders`), which start the fulfillment Saga, and Shipment events (`orchestration.shipments`), which wake it.
_Avoid_: Orchestrator, Saga service

**Idempotency key**:
The value a caller sends in the `Idempotency-Key` header so that sending a command again does nothing twice. A context records it, scoped to the caller, in the transaction of the change the command made, with the request and the response it got. A repeat of the same request from the same caller gets that response back, marked `Idempotent-Replayed: true`, and the same key with a different request is refused. Only a successful response is recorded, so a refused or failed command is evaluated afresh when sent again. Keys are kept for 7 days. Orchestration sends `<workflowId>:<runId>:<activity>`.
_Avoid_: Request ID, Correlation ID (which names a request for following it, and decides nothing)
