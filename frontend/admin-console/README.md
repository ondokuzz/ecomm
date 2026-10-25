# Admin Console

The back-office React app where Staff manage the Catalog: Products with their Variants, Prices and
Stock, and Categories with their attribute definitions; and Promotions: Coupons and Campaigns. Staff
also read every Customer's Orders here, without being able to change them, and work two queues:
Shipments to pick and pack, and RMAs to decide, receive and inspect. Built on the Storefront's stack:
Vite, React, TypeScript, React Router and TanStack Query, with `oidc-client-ts`.

## Pages

| Path | |
|---|---|
| `/products` | Every Product in a table: name, SKU, Category, Variant count and lowest Price. Filter by Category (`?category=`) and search names and SKUs (`?q=`). Edit or delete each |
| `/products/new` | A new Product, in the Category `?category=` names if any |
| `/products/{sku}` | A Product, its Variants and their Stock. The SKU and Category can't change here |
| `/products/{sku}/stock/{variantId}` | A Variant's Stock movements, newest first, 20 to a page, and whether its On-hand equals their sum. Read only |
| `/categories` | Every Category in a table: name, slug, attribute definitions (Variant axes highlighted), Product count. Edit or delete each |
| `/categories/new` | A new Category: name, slug and attribute definitions |
| `/categories/{slug}` | A Category's name and attribute definitions. The slug can't change, since Products name their Category by it |
| `/promotions/coupons` | Every Coupon in a table: code, discount, minimum, validity, how much it is used (`40 of 100 used, 2 held`) and its state (active, scheduled, expired, off or exhausted). Switch each on or off, edit or delete it. `/promotions` opens here |
| `/promotions/coupons/new` | A new Coupon |
| `/promotions/coupons/{code}` | A Coupon, with how many redemptions it has and how many Checkout Sessions hold it now. The code can't change, since Orders record it |
| `/promotions/campaigns` | Every Campaign by priority: name, discount, Categories, minimum, validity and its state, running, scheduled, over or off. Switch each on or off, edit or delete it |
| `/promotions/campaigns/new` | A new Campaign |
| `/promotions/campaigns/{id}` | A Campaign |
| `/orders` | Every Customer's Orders, newest first, 20 to a page: Order reference, when placed, Customer ID, items, total and Order Status. Filter by Status, Customer ID, the days placed and Order reference |
| `/orders/{id}` | An Order: its ID, Customer ID, lines, each Discount by its Campaign or Coupon, tax and total, its Payments with their transactions, its Shipment, its RMAs with their statuses, their replacement Shipments, its Refunds, the Notifications its Customer was sent about it, and its Order Status history |
| `/fulfillment` | The fulfillment queue: Shipments awaiting pick, picked, packed or on hold, one tab each with its count, oldest first, 20 to a page (`?status=&page=&kind=`), from the Warehouse chosen or all, of one kind or both: Order reference, "Replacement · RMA-…" for a replacement, linking to the RMA, how long ago it was paid, Warehouse, destination country and units |
| `/fulfillment/{id}` | A Shipment: its RMA for a replacement, its Order, Warehouse, Shipping option, Shipping address and lines. Pick it, pack it with a Serial number per unit, and see its tracking, its hold reason and its status history |
| `/rmas` | The RMA queue: Requested, Awaiting units, Received, On hold and Closed, one tab each with its count, 20 to a page (`?tab=&page=`): RMA reference, kind, Order reference, Product, the status where a tab holds several, and how long ago it was requested, or, awaiting units, its Send-back deadline |
| `/rmas/{id}` | An RMA: its Order, Customer, Product and Variant, quantity, Serial numbers, kind, the Customer's reason, its Refund amount, Send-back deadline, Warehouse and Inspection, the window it was checked against, its RMA history, and the Notifications its Customer was sent about it. Approve or reject a requested one, receive an approved one's units, inspect a received one, and see its resolution |
| `/reviews` | Review moderation: held reviews and published ones with open reports, oldest first, 20 to a page (`?page=`): the review's title or start, its author, Product, rating, status, open reports by reason, and how long ago it was posted |
| `/reviews/:id` | A review, whatever its status: its Product, Variant, author and Customer ID, what it says, its reports and its moderation history; publish, dismiss its reports or remove it |
| `/message-templates` | Message templates: each Message type on each Notification channel it goes on, as Notifications renders it with its sample content: an email's subject, its HTML in a sandboxed frame and its plain text, an SMS's text, a push's title and text. Read only |

`/` opens the Products. Every page needs a Staff sign-in.

The editor lists the attribute definitions as a table, one row each: name, type (`TEXT`, `NUMBER`,
`BOOLEAN` or `ENUM`), an `ENUM`'s values as one comma-separated line, whether it is required and
whether it is a Variant axis. Rows can be added, removed and moved up or down, since Products show
their attributes in this order. `formOf` and `categoryRequest` in
[`src/domain/category.ts`](./src/domain/category.ts) convert between a Category and the form.

Deleting a Category goes through a ConfirmDialog. Catalog refuses with a 409 while the Category
still has Products, and the list shows its message.

## The Product editor

Choosing the Category comes first, since it decides the rest of the form. Its non-axis attribute
definitions become the attribute fields, each as its type takes it: text, a decimal for a `NUMBER`,
a choice of yes or no for a `BOOLEAN` and of its values for an `ENUM`. Its Variant axes become
columns of the Variants table. Choosing another Category clears the attribute and axis values
already typed. Once the Product is saved, its Category and SKU are fixed in the editor.

**Warranty and Serial tracking** apply to all of the Product's Variants. Warranty months (1 to
120) and the manufacturer give its Warranty terms; leaving both blank means no manufacturer's
warranty, and filling in only one is refused beside the other. Serial tracking is None, Serial
number or IMEI, which says what packing records for each unit; a new Product starts at None.
Catalog's own refusals, `warranty.months`, `warranty.manufacturer` and `serialTracking`, show beside
their fields.

The Variants table has one row per Variant:

- **Variant ID.** Typed for a new row, usually the SKU for the first. A saved Variant's ID is shown
  read-only, since Carts, Orders and Stock name it.
- **One value per Variant axis.**
- **Price**, typed as a decimal such as `799.00` in the Product's one currency and sent as Money, `{"amountMinor": 79900, "currency": "EUR"}`. `moneyOf` and
  `decimalOf` in [`src/domain/money.ts`](./src/domain/money.ts) convert digit by digit, so no
  floating-point rounding creeps in. A decimal with more fraction digits than the currency has,
  such as `799.001`, or with a thousands separator, is refused beside the field before anything is
  sent.

  The currency is `EUR` unless changed, and must be one of the Currencies Catalog lists at
  `GET /currencies`, which the field suggests. Prices are read and written by the Minor unit
  Catalog gives each Currency, never the browser's: browsers' `Intl` data says HUF has 0 digits
  where ISO 4217 says 2, so `1000` would become a hundredth of the Price meant (see the
  [Catalog README](../../services/catalog/README.md#currencies)). The editor waits for the list,
  and a currency outside it, such as `ABC` or `XXX`, is refused beside the field. So is Catalog's
  own refusal of a Variant's currency, `variants[i].price.currency`.
- **Images**, separated by commas. When there are any, they replace the Product's own, which are
  typed one per line above the table.
- **On hand**, the units Inventory physically holds, which Staff set. **Reserved** and
  **Available** are shown read-only as Inventory reports them: what Reservations hold for checkouts,
  and what is left to sell.

A new row starts with 0 on hand. A blank count leaves the Variant's Stock alone, and is how a
Variant Inventory doesn't stock yet shows.

Saving sends the Product to Catalog (`POST /products` or `PUT /products/{sku}`), then each on-hand
count that changed, or belongs to a Variant Inventory doesn't stock yet, to Inventory
(`PUT /stock/{variantId}`, Staff only). Catalog must have the Variants first. If Inventory refuses a
count, such as one below what Reservations hold, the Product stays saved and the editor stays open,
showing Inventory's message beside that count. Saving again then updates the Product and retries
the counts still to set. A new Product moves to its own page, `/products/{sku}`, keeping the
refused counts as typed with their messages, so a reload or Back finds the saved Product rather
than an empty form. A reload shows the counts as Inventory has them. `productRequest` and `stockChanges` in
[`src/domain/product.ts`](./src/domain/product.ts) turn the form into those requests.

Catalog keeps every Variant a Product has had until the Product is deleted. Removing a saved row
and saving is refused with a 409 naming the Variant IDs it must keep, shown above the form.

Deleting a Product goes through a ConfirmDialog. It takes the Variants with it, then has Inventory
stop stocking each (`DELETE /stock/{variantId}`, Staff only). Catalog goes first, so nothing more
can be bought. A Variant Inventory doesn't stock is skipped. Inventory keeps the Stock of a Variant
that Reservations still hold, and the list names each such Variant with Inventory's reason.

## Stock movements

A stocked Variant's row in the editor has a **Movements** link to
`/products/{sku}/stock/{variantId}`, which pages through Inventory's
`GET /stock/{variantId}/movements`, Staff only, newest first (see the
[Inventory README](../../services/inventory/README.md#stock-movements)). The page lives in the URL
as `?page=`. Each movement shows its kind (Adjusted, Reserved, Released, Committed, Received or
Restocked), its On-hand change and reserved change, signed as `+3` or `−2`, the Reservation that
made it, Staff's reason, its reference and its time to the second. The reference is the RMA a
return's restock or a replacement's Reservation was for, as its RMA reference (`RMA-3F2A9C1B`);
Inventory names a replacement's RMA by ID, so that one links to its RMA page. Showing it as an RMA
reference follows Returns & Warranty's rule, `RMA-` and the first eight characters of the ID
upper-cased (its `Rma.reference()`), which `referenceOf` repeats here: a second copy, kept on
purpose (#100) since the ID gives the link a bare reference couldn't. A change to that rule changes
both. Above them are the Variant's On-hand, the
On-hand its movements add up to, and whether the two balance. `movementRowsOf` and `balanceOf` in
[`src/domain/stock.ts`](./src/domain/stock.ts) do this. The page is read only: Staff still change
only On-hand, in the editor, and each change shows here as an Adjusted movement with no reason.

## Promotions

Promotions is one section of the navigation, with a tab each for Coupons and Campaigns. Their
editors share the discount, the minimum subtotal, the validity window and the switch:

- **Discount.** A percentage off, a whole number from 1 to 100, or a fixed amount off.
- **Money.** The amount off and the minimum are each typed as a decimal in one of the Currencies
  Catalog prices in, as a Price is, and sent as Money (see [Money](#the-product-editor) above). A
  blank minimum is none.
- **Validity.** From and until, in the browser's own time zone, to the minute. They are sent as
  instants, so a Campaign saved in Istanbul starts at the same moment for Staff in Lisbon. Seconds
  are dropped, so saving a seeded Coupon or Campaign rounds its window down to the minute.
- **Switched on.** Each list also switches a Coupon or Campaign on or off in one click, sending it
  back as it is with only `active` changed.

A new Coupon or Campaign starts as a percentage off, switched on, from the current minute for a week.
A Coupon also has its code, typed once, and its Usage limits: a usage limit in all and a
per-Customer limit, each a whole number of at least 1, blank for none. Lowering one keeps the uses
Checkout Sessions already hold; raising it lets the Coupon apply again. A Campaign has a name, a priority, which no other Campaign
may have, and the Categories it is limited to, ticked from Catalog's list. None ticked means every
line. A Category the Campaign has but Catalog no longer does is still listed, marked "not in
Catalog", so it can be unticked.

`formOf`, `couponRequest` and `campaignRequest` in [`src/domain/coupon.ts`](./src/domain/coupon.ts)
and [`src/domain/campaign.ts`](./src/domain/campaign.ts), over the shared terms in
[`src/domain/promotion.ts`](./src/domain/promotion.ts), convert between each and its form. Before
sending, they name a percentage, an amount, a currency, a time or a priority that can't be sent,
by the field Promotions would name. Promotions checks the rest, such as a code that isn't one, a
window that ends before it starts, a taken priority or a Category Catalog doesn't have.

Deleting goes through a ConfirmDialog, which suggests switching off instead.

## Orders

Orders are read only. Nothing on these pages changes an Order; only Checkout does that. The list
calls Order Management's `GET /staff/orders` and the page `GET /staff/orders/{id}`, both Staff only
(see the [Order Management README](../../services/order-management/README.md#staffs-view)).

The filters and the page live in the URL, as `?status=&customer=&from=&to=&reference=&page=`, so a
filtered list can be reloaded or shared. Filtering goes back to the first page.

- **Placed from** and **Placed to** are days in the browser's time zone, and both are included:
  they are sent as the instants of the first day's midnight and of the midnight after the last.
- **Order reference** is what a Customer quotes, such as `#3F2A9C1B`. The `#` is optional, and a
  full Order ID works too. Anything but hex digits and hyphens is refused beside the field before
  anything is sent.

The Order page's back link returns to the list as it was filtered and paged. Its Status history is
newest first, each change with its time to the second, in the browser's time zone, and the caller
that made it, such as Checkout. A change backfilled for an Order placed before histories were kept
has no time to show, and says so. `filtersOf`, `searchOf`, `ordersQueryOf` and `historyRowsOf` in
[`src/domain/order.ts`](./src/domain/order.ts) do this.

The Order page lists the Order's Payments from Payment's `GET /staff/payments?orderId=`, Staff
only, newest first (see the [Payment README](../../services/payment/README.md)). Each shows its
status (Pending, Authorized, Declined with the gateway's decline reason, Voided, or Charged for
`CAPTURED`), amount, gateway reference and ID, and its Payment transactions oldest first: kind
(Authorization, Void or Capture), outcome (Approved, Declined or Pending), amount, the gateway's
reference, decline reason and time. A capture the gateway refused is listed as a declined Capture
with its reason, such as `authorization_expired`, and the Payment stays Authorized.
A transaction backfilled from a Payment recorded before the ledger says its time is approximate.
`transactionRowsOf` in [`src/domain/payment.ts`](./src/domain/payment.ts) does this. An Order with
no Payment, such as one whose payment gateway failed to answer, says none was recorded. Like the
rest of the page, Payments are read only.

The Order page also lists the Order's RMAs, oldest first, from Returns & Warranty's
`GET /rmas?orderId=`: each RMA reference links to its page, with its kind, Product, quantity, when it
was requested and its status. Above them, its Shipment from Fulfillment & Shipping's
`GET /shipments?orderId=`: its status, Warehouse, tracking number once dispatched, and a link to
its page. An Order not yet paid, or placed before Sprint 5, has none, and says so. Below them, once
an RMA's fault is confirmed, its replacement Shipment from `GET /shipments?rmaId=`, with its status,
Warehouse, tracking number and a link; one the returns Saga refunded instead, for want of Stock, has
none. Then the Refunds of its Payments, oldest first, whatever their outcome: the RMA each was for,
linking to it, its outcome, amount, the gateway's reference and time. `refundRowsOf` in
[`src/domain/payment.ts`](./src/domain/payment.ts) gathers them.

## Fulfillment

The fulfillment queue and the Shipment page call Fulfillment & Shipping's `GET /warehouses`,
`GET /shipments?status=&warehouseId=&kind=&page=&size=` and `GET /shipments/{id}`, and move a Shipment with
`POST /shipments/{id}/pick` and `/pack`, all as Staff (see the
[Fulfillment & Shipping README](../../services/fulfillment-shipping/README.md)). The fulfillment
Saga creates each Shipment once its Order is paid, and dispatches or holds it once packed; the
returns Saga creates an RMA's replacement and dispatches it once packed. The console only reads
what they did.

- **The queue** has a tab for each status still in Staff's hands, Awaiting pick, Picked and Packed,
  and one for On hold, each with its count. Shipments are oldest first, so Orders are worked in the
  order they were paid; "how long ago" counts from the Shipment's creation, when the Order was
  paid. The tab and page live in the URL. The **Warehouse** filter is remembered by the browser, in
  `localStorage`, so Staff at one site see its Shipments every time; a browser that can't store it
  forgets it when the page is left. The **Kind** filter shows Orders' Shipments, replacements, or
  both, and lives in the URL. A replacement is marked "Replacement · RMA-…", linking to the RMA.
- **The Shipment page** shows the Order reference, linking to the Order, the Shipping address and
  option, and each line's Product, Variant and quantity. A replacement is headed "Replacement for
  RMA-…" and shows its RMA, linking to it, and that no Payment is captured for it; it says Created
  where an Order's says Paid. An Awaiting pick one offers **Mark picked**.
- **Pack**, once picked, has one input per unit of each serial-tracked line: an IMEI for a phone, a
  serial number otherwise. Each is checked as it is typed: an IMEI with anything but digits, more
  than 15, or a wrong Luhn check digit is refused beside its input at once; one still short of 15
  is refused only when Pack is pressed, as is a Serial number entered twice. Fulfillment &
  Shipping's 422 violations, named `serialNumbers.<variantId>[<unit>]` as the inputs are, show beside
  the same inputs, and a 409 `serialAlreadyShipped` beside the input holding that Serial number. A
  Shipment moved on elsewhere first answers 409 `illegalTransition`, which the page says.
- **After packing**, the page asks again every 3 seconds while the Shipment is Packed or Shipped.
  Once the fulfillment Saga captures the Payment and dispatches it, the page shows the Carrier, the
  tracking number and the Tracking events, newest first, and when it was delivered. If the capture
  was refused, the Shipment is On hold with the payment gateway's reason, and stays out of the
  Carrier's hands.
- **The status history** is newest first: the status, when to the second, and who: a Staff
  member's ID, the Fulfillment Saga (the Returns Saga for a replacement) or the Carrier. The Order page's Status history likewise names
  the Fulfillment Saga for Fulfilled, Shipped and Delivered, and the Checkout Saga for the rest.

`serialCheckOf`, `packRequestOf`, `queueOf`, `queueSearchOf`, `shipmentsQueryOf`, `unitsOf` and
`shipmentHistoryRowsOf` in [`src/domain/shipment.ts`](./src/domain/shipment.ts) do this.

## RMAs

The RMA queue and the RMA page call Returns & Warranty's `GET /rmas?status=&page=&size=` and
`GET /rmas/{id}`, decide with `POST /rmas/{id}/approve` and `/reject`, and take the next steps with
`/receive` and `/inspect`, each under an `Idempotency-Key` of its own, all as Staff (see the
[Returns & Warranty README](../../services/returns-warranty/README.md#api)). Customers request RMAs
through the API until the Storefront's form lands. The returns Saga takes it from the Inspection, and
the console only reads what it did.

- **The queue** has a tab per stage of the RMA lifecycle, each with its count: Requested (`REQUESTED`),
  Awaiting units (`APPROVED`), Received (`RECEIVED` and `INSPECTED`, while the Saga resolves it), On
  hold (`ON_HOLD`) and Closed (`REJECTED`, `EXPIRED`, `REFUNDED`, `EXCHANGED` and `CLOSED`). Awaiting
  units is by Send-back deadline, soonest first, and flags each deadline within 2 days, or passed,
  which the Saga is about to expire; every other tab is oldest request first, so none waits
  forgotten. A tab of several statuses shows each RMA's. The tab and the page live in the URL, and
  the RMA page's back link returns to them. "How long ago" counts from when the page was fetched, in
  whole minutes, hours or days.
- **The RMA page** shows what it claims, the Customer's reason, its Refund amount, and, once
  approved, its Send-back deadline and the Warehouse its units go back to; and the window it was
  checked against, as it was then: the Return Period, or the Warranty Window with its Warranty
  terms, such as "12 months, from JBL", each from delivery to its end, in the browser's time zone.
  Last, the Notifications its Customer was emailed about each step, from Notifications' `GET
  /notifications?rmaId=`, listed as on the Order page.
- **Deciding**: a requested RMA can be approved, with an optional note, or rejected, with a reason
  the Customer is told. A rejection without a reason, or either text over 1,000 characters, is
  refused beside its field before anything is sent. Once decided, the page offers neither again; an
  RMA another Staff member decided first answers 409 `rmaAlreadyDecided`, which the page says,
  showing the RMA as they left it.
- **Receiving** an approved RMA takes one input per unit of a serial-tracked Variant, each checked
  as it is typed against the RMA's Serial numbers, whatever its case: one that can't become one of
  them, or one typed twice, is refused at once, and an empty or unfinished one when Receive is
  pressed. Any other Variant's units are received by confirming their quantity. Returns & Warranty's
  422 `serialMismatch` shows beside each input holding a Serial number it refused, `quantityMismatch`
  beside the quantity (or the units, for a serial-tracked Variant), and a 400 beside the field it
  names. A Receive or Inspection sent again unchanged, after an answer that never came, keeps its
  `Idempotency-Key`, so Returns & Warranty replays its first answer.
- **Inspecting** a received RMA offers the outcomes for its kind: Accepted or Refused for a return,
  Fault confirmed or No fault found for a warranty claim. A refusing one needs a note, refused
  beside it before anything is sent. An RMA another Staff member moved on first answers 409
  `transitionRefused`, which the page says.
- **The resolution**, once inspected: while the returns Saga is at work the page asks again every
  3 seconds. Then it shows the Refund's amount and its reference at the payment gateway; the
  replacement Shipment's status, Warehouse, tracking number and a link, from the moment the Saga
  creates it; the Inspection's note for one closed; or, for one on hold, the reason its Refund was
  declined.
- **The RMA history** is newest first: each step, when to the second, who took it (the Customer,
  a Staff member's ID, or the Returns Saga), and its note: an approval's, a rejection's reason, an
  Inspection's, a hold's reason.

`queueTabs`, `queueOf`, `queueSearchOf`, `rmasQueryOf`, `sendBackOf`, `windowOf`, `historyRowsOf`,
`decisionOf`, `receivedChecksOf`, `receiveOf`, `receiveRefusalsOf`, `outcomesOf`, `inspectionOf` and `resolutionOf` in
[`src/domain/rma.ts`](./src/domain/rma.ts), and `formatAgo` in
[`src/domain/ago.ts`](./src/domain/ago.ts), do this.

## Review moderation

The **Reviews** page and a review's page call Reviews & Ratings' `GET /moderation/reviews?page=&size=`
and `GET /moderation/reviews/{id}`, and moderate with `POST /reviews/{id}/publish`,
`/dismiss-reports` and `/remove`, as Staff (see the
[Reviews & Ratings README](../../services/reviews-ratings/README.md#moderation)). Product names come
from Catalog's Product list.

- **The queue** lists what awaits Staff, oldest review first: a review the word screen held, and a
  published one Customers reported, with its open reports counted by reason, most first, such as
  "2 offensive · 1 spam". The page lives in the URL, and a review's back link returns to it.
- **A review's page** shows its Product, linked to its editor, the Variant bought, its author's
  display name and Customer ID, its rating, when it was posted and edited, and its text; then every
  report, with its reason, note, Customer ID, time and whether it is open; and its moderation
  history, newest first, each action with its time, Staff member's ID and a removal's reason.
- **Moderating**: a held review can be published; a published one with open reports can have them
  dismissed; either can be removed with a reason its author is told, refused beside its field before
  anything is sent if it is blank or over 1,000 characters. Each action resolves the review's
  reports, so it leaves the queue. A removed review offers nothing. One another Staff member
  moderated first answers 409 (`notHeld`, `noOpenReports` or `alreadyRemoved`), which the page says,
  showing the review as they left it.

`openReportsLabel`, `actionsOf`, `removalOf` and `historyRowsOf` in
[`src/domain/review.ts`](./src/domain/review.ts) do this.

## Message templates

The **Messages** page calls Notifications' `GET /templates`, which renders every Message template
with its Message type's sample content for a made-up Customer, as the service's start-up check does.
Each Message type and channel is a panel, named for both: its name and code, the channel, the
locale, and for email the subject, the HTML body in an `iframe` with an empty `sandbox` (no scripts,
no forms, no navigation) and the text body under "Plain text"; for SMS its text; for push its title
and text.

An Order's page lists the Notifications its Customer was sent about it, from Notifications' `GET
/notifications?orderId=`, newest first: the Message type, the channel, the recipient as
Notifications masks it, the status (with the last attempt's reason for a pending or failed one) and
when, and under "Message" the subject, or a push's title, and the message as rendered: an email's
HTML in a sandboxed frame, an SMS's or push's text. Staff can't edit the templates, which live in the
[Notifications](../../services/notifications/README.md#message-templates) service.

## Validation errors

Catalog and Promotions check every write. A 400 names each offending field in its problem detail's `errors`,
such as a Category's `attributes[1].values` or a Product's `attributes.screen`,
`variants[1].axisValues.storage` or, for two Variants alike, `variants[2].axisValues` (see the
[Catalog README](../../services/catalog/README.md#categories-and-attribute-definitions)), or a
Campaign's `discount.amountOff.currency` or `categories[1]` (see the
[Promotions README](../../services/promotions/README.md#errors)). A Campaign's Category errors show
under its Categories, each with the Category's slug.
`fieldErrorsOf` in [`src/api/fieldErrors.ts`](./src/api/fieldErrors.ts) turns those into a message
per field, which the editor shows beside the field, marking it `aria-invalid`. A message stays
until its field is edited. Adding, removing or moving a row clears every row's messages, since the
row numbers they name have changed. The failure as a whole shows above the form, in Catalog's
words, with its support reference. So does a mistake Catalog doesn't tie to a field, such as a
missing SKU or a Variant ID another Product has.

## Auth

`oidc-client-ts` (through `react-oidc-context`) signs Staff in with Authorization Code + PKCE
against the realm's public `admin-console` client
([Identity & Access](../../services/identity-access/README.md)). Tokens are kept in memory only, as in the Storefront. A reload
gets them back from the Keycloak session in a hidden iframe at `/silent-renew`, and silent renew
refreshes them. A signed-out visitor goes straight to Keycloak's login page, wearing the
Storefront's brand, and comes back to the page they asked for. A 401 later, when the Keycloak
session has ended, sends them to sign in again.

Only Staff get in. A signed-in user whose access token lacks the `STAFF` realm role, such as the
demo Customer, sees "This console is for Staff" and a "Sign out" button (`RequireStaff` in
[`src/auth/auth.tsx`](./src/auth/auth.tsx), `isStaff` in [`src/auth/roles.ts`](./src/auth/roles.ts)).
That check only decides what the console shows. Catalog checks the token and the role on every
Staff endpoint.

## Look

The console wears the Storefront's design tokens, fonts and favicon, so there is one source of
truth. [`src/brand/`](./src/brand) and `public/favicon.svg` are copied from the Storefront by its
[`scripts/admin-console-brand.ts`](../storefront/scripts/admin-console-brand.ts). Don't edit them
here. After changing the Storefront's tokens, fonts or favicon, run this in `frontend/storefront`:

```sh
npm run admin-console-brand
```

The Storefront's tests fail until you do.
[`src/index.css`](./src/index.css) is the console's own stylesheet, written against those tokens.
It is denser than the Storefront and built around tables: smaller type, compact controls and
squarer corners. Dark mode follows `prefers-color-scheme`, as in the Storefront.

## Calling the services

The browser reaches Catalog at `/api/catalog/…`, Inventory at `/api/inventory/…`, Order Management
at `/api/order-management/…`, Payment at `/api/payment/…`, Fulfillment & Shipping at `/api/fulfillment-shipping/…`, Promotions at `/api/promotions/…` and Returns & Warranty at
`/api/returns-warranty/…` on the console's own origin. Everything under
`/api/` goes to the [API gateway](../../platform/api-gateway/README.md), which checks the token and
routes it. In development the Vite dev proxy forwards `/api` to the gateway's compose host port,
8000 ([`vite.config.ts`](./vite.config.ts)). In compose, nginx does it
([`nginx.conf`](./nginx.conf)), giving every request a Correlation ID as the Storefront's nginx
does. Errors show the service's message and that ID as their support reference.

## Run it

In compose, nginx serves the production build on http://localhost:8090:

```sh
docker compose up -d --build admin-console   # from the repo root
```

For development, run the stack in compose and Vite on http://localhost:5174 (both origins are
allowed redirects of the `admin-console` client):

```sh
npm install
npm run dev
```

Sign in as `staff@ecomm.local` / `staff`. `demo@ecomm.local` / `demo` is a Customer and is refused.

A realm imported before the `admin-console` client had its redirect URIs refuses the login with
"Invalid parameter: redirect_uri". The
[Identity & Access README](../../services/identity-access/README.md#the-admin-console-client) shows
how to apply the client to an existing realm.

## Checks

```sh
npm run typecheck
npm run lint
npm test          # Vitest: failed requests as messages and references, field errors from problem details, the STAFF role in an access token, a Category, a Product with its Warranty terms and Serial tracking, a Coupon and a Campaign to and from their editor's form, decimals to and from Money, the Order list's filters to Order Management's query, an Order's Status history, Payment statuses and transactions, Stock movements and their balance, RMA statuses and kinds, Message type and Notification channel names and what a message's subject is called, the RMA queue's tabs, URL and query, a Send-back deadline and its flag, an RMA's window, its RMA history, a decision, a receipt and an Inspection before they are sent, each Serial number received as it is typed, Returns & Warranty's refusals of a receipt, an RMA's resolution, an Order's Refunds, Shipment and Tracking event statuses, the fulfillment queue's URL and query, an IMEI or serial number as it is typed, a Pack request, a Shipment's status history, a review's open reports, what Staff may do to it, a removal before it is sent and its moderation history, and how long ago
npm run build
npm run test:e2e  # Playwright against the running compose stack (`make up`)
```

The Playwright tests sign in on Keycloak as `staff@ecomm.local`. What the fulfillment and RMA tests
share, from tokens to a Customer of their own and buying a phone through the gateway, is in
[`e2e/stack.ts`](./e2e/stack.ts).
[`e2e/promotions.spec.ts`](./e2e/promotions.spec.ts) creates, edits, switches off and deletes a
Coupon and a Campaign, checking each through Promotions' API, and sees Promotions refuse a window
that ends before it starts and the seeded Audio week's priority beside the field. It creates a
Coupon with a usage limit of 1, buys a phone with it as a Customer of its own through the gateway
(`buy` in `e2e/stack.ts`), and sees the Coupon `1 of 1 used` and `Exhausted` once the checkout Saga
has redeemed it, deleting the Customer and the Coupon after. It also creates a
Campaign of 25% off `laptops`, then, as a Customer it makes in Keycloak for the test (through
Keycloak's admin API, as `admin`), puts a laptop in the Cart, opens checkout on the Storefront in a
browser of the Customer's own, and sees the Campaign's Discount there without entering anything. It
pays, so no Stock stays held, finds the Discount on the Order, and then, as Staff, finds that Order
by its reference and reads its history, Placed then Paid.
[`e2e/reviews.spec.ts`](./e2e/reviews.spec.ts) has two Customers of its own buy a phone through the
gateway and review it: the first's trips the word screen and is held, the second's is published and
reported by the first. Staff find the held review in the queue on the page its age puts it, publish
it and see it on the Product's public list; then find the reported one with "1 off-topic", see the
report's note, have a removal without a reason refused, remove it with one, and see it Removed with
its reports resolved, gone from the public list, and its reason in its author's eligibility. Both
reviews are deleted by their authors after, and the Customers with them.
[`e2e/products.spec.ts`](./e2e/products.spec.ts) creates a Product in `phones` with two Variants and
their Stock.

- **The main flow** checks Catalog and Inventory through their APIs. On the Storefront it picks
  the second Variant and sees its Price and Stock. Then it deletes the Product through the
  ConfirmDialog and checks that Catalog and Inventory no longer have it or its Stock.
- **A refused count** has Inventory refuse one Variant's count. The test stands in for Inventory
  there, since a new Variant has no Reservations. It checks that the new Product opens on its own
  page with the refusal beside that count, then sets the count by saving again.
- **Warranty terms** gives the new Product 200 months and sees them refused beside the field, then
  24 months from Ecomm with IMEI Serial tracking, checks both through Catalog's API, and sees the
  Storefront's Product page show "24 months, manufacturer's warranty (Ecomm)".
- **Stock movements** opens the new Product, sets the first Variant's On-hand from 5 to 8, and
  opens its movements: balanced at 8, the Adjusted movement of `+3` above the opening `+5`.
- **A Price in search** waits for the new Product to reach Search & Discovery, changes its first
  Variant's Price in the editor, and sees search give the new "from" Price within 10 seconds, on its
  API and on the Storefront's listing.

[`e2e/orders.spec.ts`](./e2e/orders.spec.ts) places an Order for a Customer of its own as Checkout
does, straight at Order Management on its host port (`ORDER_MANAGEMENT_URL`, default
`http://localhost:8085`), since the gateway never routes placement. It pays and cancels the Order,
then finds it in the console by its Order reference and by its Customer and Status, opens it and
reads its history, each change with its time and caller. Orders are never deleted, so the test's
Order stays, Cancelled. A second test places an Order the same way, authorizes its Payment with
`tok_approve` straight at Payment on its host port (`PAYMENT_URL`, default `http://localhost:8084`)
and marks it paid, then opens it in the console and sees the Payment Authorized with its
authorization transaction: Approved, with the gateway's reference and time.

[`e2e/templates.spec.ts`](./e2e/templates.spec.ts) opens Messages and sees the Order confirmation's
email previewed: its subject for the sample Order `#3F2A9C1B`, "Hello Ada," and a sample line in the
HTML frame, and the total in its plain text.

[`e2e/rmas.spec.ts`](./e2e/rmas.spec.ts) places an Order of two JBL Flip 6 speakers for the demo
Customer, `demo@ecomm.local`, the same way, and leaves it placed, so no Saga takes it up. It creates
and dispatches its Shipment as the fulfillment Saga does, straight at Fulfillment & Shipping on its
host port (`FULFILLMENT_SHIPPING_URL`, default `http://localhost:8099`), picking and packing it as
Staff through the gateway with Serial numbers of the run's own, and waits for the mock carrier to
deliver it, about 30 seconds. As the demo Customer it then requests a return of one speaker and a
warranty claim on the other through the gateway. In the console it finds the return on the queue's
last page, checks its page, approves it with a note and sees the decision in its history; then it
opens the warranty claim, sees its Warranty Window of 12 months from JBL, is refused a rejection
without a reason, and rejects it with one, which Mailpit has one email of, to the demo Customer,
with the reason; then it opens the Closed tab. The Order page lists both RMAs with their statuses.

A second test there needs the fulfillment and returns Sagas. As a Customer of its own it buys a
seeded phone through the gateway, packs its Shipment through the API with an IMEI of the run's own,
and waits while the fulfillment Saga captures the Payment and dispatches it and the mock carrier
delivers it. It requests a return of the phone as that Customer and approves it as Staff, through
the API, and Mailpit has one email of each: the request, and the approval with `WH-BER`'s Return
address and the Send-back deadline. In the console the RMA page shows its Send-back deadline,
Warehouse and Refund amount; another IMEI is refused as it is typed, and Receive sends nothing; the
right one receives it, and its email follows. Staff inspect it as Accepted, and within the Saga's
run the page shows it Refunded, with the Refund's amount and the gateway reference Returns &
Warranty recorded. Reloaded, it lists the three emails, newest first, sent to the masked address,
the approval's HTML showing the Return address. The Order page lists the RMA as Refunded and the
Refund, Approved, with the same reference.

[`e2e/fulfillment.spec.ts`](./e2e/fulfillment.spec.ts) needs the fulfillment Saga. As a Customer
of its own it buys a seeded phone through the gateway, shipped to Berlin, Standard, and pays with
`tok_approve`. Once the Saga has created the Shipment, the console finds it in the queue under the
Berlin Warehouse (remembered past a reload) with its country and how long ago it was paid, picks
it, has an IMEI with a wrong check digit refused as typed, and packs it with a valid one of the
run's own. Within a minute the Saga captures the Payment and dispatches it: the page shows it
Shipped by the Fulfillment Saga with the tracking number Fulfillment & Shipping gives. Notifications
emails the Customer "Your Order #… is on its way" with that tracking number, once, read from
Mailpit's API (`MAILPIT_URL`, default `http://localhost:8025`); the Order page shows the Shipment and
links back to it, and lists the shipped email, sent to the masked address, with its message, under
the Order's confirmation. A second Order, paid with
`tok_capture_decline`, is picked and packed through the API; its Shipment goes on hold with the
gateway's reason, without tracking, and is listed under On hold.

Each test removes its Product and Stock, Coupon, Campaign or Customer through the services' and
Keycloak's APIs if it failed before doing so itself, so the stack is left as it was, but for the
Orders the Order and Campaign tests place, the RMA tests' Orders, Shipments and RMAs, and the fulfillment test's two Orders and Shipments. It runs against the console on 8090; set `ADMIN_CONSOLE_URL` to use Vite's
5174, and `STOREFRONT_URL` or `KEYCLOAK_URL` for other hosts. Install Chromium once with
`npx playwright install chromium`.
