# Storefront

The Customer-facing React app: search and filter the Products, pick a Variant and see its Price and
Stock, keep a Cart, check out with a mock payment's test cards, and follow Orders and their Order Status. Built with Vite,
React, TypeScript, React Router and TanStack Query.

## Pages

| Path | | Login |
|---|---|---|
| `/?q=&category=&…` | Products, searched and filtered, as Search & Discovery finds them: see [Search](#search) | no |
| `/products/{sku}?variant=` | A Product with a Variant picker, and the chosen Variant's image, Price, Stock and specs, laid out by its Category's attribute definitions; add it to the Cart. Below, its ratings and reviews: see [Reviews](#reviews) | no (adding and reviewing need it) |
| `/cart` | The Cart, priced from Catalog's current Prices; change quantities, remove lines or empty it | yes |
| `/checkout` | Starts or resumes a Checkout Session: its lines, price breakdown, a Coupon field and a countdown to when it expires, a test-card picker and a "Pay" button | yes |
| `/orders/{id}` | An Order: its Order Status timeline, drawn from its Order Status history with times, its lines and summary; after checkout, the confirmation first | yes |
| `/orders?page=` | My Orders as cards, newest first, 12 to a page; `page` counts from 1 | yes |

## Design

The look lives in [`src/index.css`](./src/index.css): design tokens (colours, gradients, fonts, spacing,
corner radii, shadows) as CSS custom properties on `:root`, redefined for dark mode, which follows
`prefers-color-scheme`. Everything else reads the tokens rather than one-off values. The fonts, Inter,
and Bricolage Grotesque for headings, are bundled through Fontsource rather than loaded from a CDN.

The pages build from the shared components in [`src/components/ui`](./src/components/ui): Button,
Card, Badge, Input, QuantityStepper, Skeleton, Icon, Toast and ConfirmDialog. Stars, in
[`StarRating.tsx`](./src/components/StarRating.tsx), show a rating to the nearest half star and pick one in the review form. The header's account menu and the
small-screen menu are native `popover` elements, so the browser handles closing them on Escape or an
outside click.

A toast is a short message in the corner, such as "Added to cart" with a "View cart" link: any page
under the `Toaster` in `App` shows one with `useToast()`. Toasts sit in a polite `aria-live` region,
so screen readers announce them, and never take focus. Each goes after 5 seconds, or sooner with its
close button; the countdown waits while a pointer rests on it or focus is inside it. At most three
show at once.

ConfirmDialog asks before an action that can't be undone, such as "Empty cart", in a native modal
`<dialog>` rather than the browser's `confirm()`: focus stays inside it, Escape or a click outside
cancels, and Cancel has focus first so Enter never confirms by accident.

The Product page's Stock indicator reads "In stock", "Only N left" at 5 or fewer
(`lowStockThreshold` in [`src/domain/stock.ts`](./src/domain/stock.ts)), or "Out of stock", which
also disables "Add to cart".

## Variants

A Product with more than one Variant gets a Variant picker: a group of radio chips per Variant axis,
in its Category's order, built by `variantAxes` in [`src/domain/catalog.ts`](./src/domain/catalog.ts).
Picking a value keeps the chosen Variant's other axis values, so a value no Variant has alongside
them (Porcelain in 256 GB, say) is disabled rather than jumping somewhere unexpected. A value whose
Variant is sold out stays pickable and is marked "Sold out". The Price, the Stock indicator, the
image (the Variant's own, when it has one) and the axis rows of the specs all follow the chosen
Variant. The choice lives in `?variant=`, so a reload or a shared link keeps it; without one, or
with one the Product doesn't have, the page shows the first Variant.

Product cards show Search's `priceFrom`, prefixed "From" when the Variants' Prices differ, and the
Product's average rating and review count once it has a review.

Cart and Order lines hold Variant IDs, so each is looked up through Catalog's
`GET /variants/{id}` and named by its Product and axis values, such as "Google Pixel 9 · Obsidian ·
256 GB"; it links to the Product page with that Variant chosen.

## Search

The Product list is [Search & Discovery](../../services/search-discovery/README.md)'s answer to
`GET /search`: a page of Product summaries, how many matched, and the Facets to narrow them by,
each counted as if its own choice weren't made.

- **Search box.** Words to find in the names, descriptions and attribute values. A search shows
  "Results for “…”" with the count, and the sort offers "Best match" first.
- **Category chips.** "All" and each Category with how many Products it would leave. One that would
  leave none is shown disabled. Choosing another Category drops the attribute filters, which were the
  old one's.
- **Filter panel.** Once a Category is chosen, each of its attributes: values to tick, with their
  counts, any of which will do, or a `NUMBER`'s range between its lowest and highest value. A value
  that would leave nothing is disabled, unless it is ticked, so it can always be cleared. Then the
  price range, typed as decimals in a Currency and sent in its Minor unit by Catalog's Currencies,
  with the lowest and highest Price as placeholders. On a phone the panel folds away behind
  "Filters".
- **Toolbar.** The count, "In stock only" with how many are, and the sort: newest, or by Price either way.
- **Cards.** A Product none of whose Variants is in Stock is marked "Out of stock".
- **Paging.** 24 to a page, with "Previous page" and "Next page".
- **Nothing found.** "No products match", with "Clear filters" when filters are set, which keeps the
  text and the sort. A page past the last says so and links to the first.

Everything is held in the URL, in the same terms `GET /search` takes, so a reload, Back or a shared
link shows the same view: `q`, `category`, `attr.<name>` (repeated for each value), `range.<name>=<min>..<max>`,
`currency` and `price=<min>..<max>` in its Minor unit, `inStock=true`, `sort` and `page`, which
counts from 1 here. Every change but paging goes back to the first page. Whatever Search would refuse
in a mangled link, such as an unknown sort or a range upside down, is left out rather than failing
the page. [`src/domain/search.ts`](./src/domain/search.ts) maps between the URL, the search and the
Facets. While a changed search loads, the last results stay in place.

A Product reaches the list a moment after Catalog publishes it, once Search has the event; its own
page reads Catalog directly, so it is there at once.

## Reviews

Ratings and reviews come from [Reviews & Ratings](../../services/reviews-ratings/README.md). A page of
the listing asks `GET /rating-summaries` for all its Products' summaries in one request, and a card shows its
stars, average and count; a card without reviews shows none, and the cards never wait for them.

A Product page shows its stars and review count under its name, linking down to **Ratings &
reviews**: the average, the stars, the count and a bar per star, then the reviews, newest first, five
to a page, each with its author, date, "edited" if it was, and the Variant bought when the Product
has several. Beside them, a Customer who isn't signed in is offered to log in; a signed-in one
sees, from `GET /products/{sku}/eligibility`, the form to write a review, their own review with
Edit and Delete, or why they can't review: "Only Customers who have bought this product can review
it." Deleting asks first.

The form checks itself before sending: a rating from 1 to 5 stars, a title of at most 120
characters and a body of at most 2,000, both counted trimmed, with a counter under each. Each
problem shows under its field once the Customer has pressed the button, and clears as they fix it.
[`src/domain/reviews.ts`](./src/domain/reviews.ts) holds those rules, the stars' fill and the
per-star shares. Posting, editing or deleting reloads the Product's summary, reviews and
eligibility, and the listing's summaries.

## Orders

After checkout the Order page opens with a confirmation: a big check, confetti that falls once, the
Order's reference, item count and total, and "Continue shopping" / "View my orders". The confetti
is decorative and hidden outright when the Customer prefers reduced motion.

My Orders shows 12 Orders to a page, newest first, with "Newer orders" and "Older orders" links and
the page in the URL (`/orders?page=2`) once there is more than one page.

An Order is shown by its Order reference (`#3F2A9C1B`, the start of its ID; the full ID is in its
summary). Its page has an Order Status timeline drawn from the Order's Order Status history: every
Status it has been in, each with when it got there, the current one marked, then, while it is on the
main path, the steps still to come up to Delivered. A cancelled Order ends at Cancelled, in red,
after Paid if it was paid, and a returned one at Returned after Delivered, in amber. A backfilled
step, from an Order placed before histories were kept, shows no time, since its real time was never
recorded. Its lines name their Products and Variants and show their thumbnails, looked up from Catalog,
and fall back to the Variant ID when Catalog no longer has the Variant. Its summary shows what it comes to, as
Order Management worked it out: the subtotal, a Discount line naming its Coupon when there is one,
the tax and the total.

My Orders lists them as cards: reference, date, the first three Products' thumbnails (and how many
more), item count, total and Order Status badge. Each Order Status badge has a colour of its own
([`orderStatusTones`](./src/components/orderStatusTones.ts)), always with its name, so colour is
never the only cue.

## Product images

A Product shows its first image through [`ProductImage`](./src/components/ProductImage.tsx), or its
chosen Variant's first when the Variant has images of its own. When there are no images, or the file
fails to load, a placeholder takes its place, so there is never a
broken-image icon. The image's `alt` text is the Product's name.

The seed Products' images are generated artwork, not photographs, so there is nothing to license: an
SVG of a phone, laptop, headphones, earbuds or speaker, in its first Variant's colour on a backdrop
in its brand's colour from the palette. A Variant with images of its own, such as the Porcelain
Pixel 9, gets one in its own colour. They live in [`public/images/products/`](./public/images/products)
at the paths Catalog's seed gives them (`/images/products/<sku-lowercase>/front.svg`, and beside it
for a Variant, such as `porcelain.svg`), and are
generated from that seed by [`scripts/generate-product-images.ts`](./scripts/generate-product-images.ts):

```sh
npm run images   # after changing services/catalog/src/main/resources/seed/products.json
```

The script is TypeScript run by Node directly, which needs Node 22.18 or later.

The seed originally named them `front.jpg`. Its paths were changed to `front.svg` rather than
rewriting `.jpg` to `.svg` at the web server, so what Catalog returns is the file that exists and its
type. Catalog loads the seed only into an empty bucket, and the seed is built into its image, so a
stack seeded before that change needs both to pick up the new paths; until then every Product shows
the placeholder. `make seed-reset` also drops every Cart, Order and Payment:

```sh
docker compose build catalog   # from the repo root; seed-reset doesn't rebuild images
make seed-reset
```

Vite serves `public/` as is in development, and the build copies it into `dist/`, which nginx
serves in compose with a one-day cache (the names don't change when the images are regenerated). A
missing image is a 404 there, not the app's `index.html`; Vite answers it with `index.html`, which
fails to load as an image just the same. A test (`npm test`) checks that every seed
Product, and every seed Variant with images of its own, has its image at the path Catalog returns,
under 50 KB.

## Auth

`oidc-client-ts` (through `react-oidc-context`) signs the Customer in with Authorization Code +
PKCE against the realm's public `storefront` client. Login and registration (`prompt=create`) both
happen on Keycloak's hosted pages, which wear the Storefront's brand through the realm's `ecomm`
login theme ([Identity & Access](../../services/identity-access/README.md#login-theme)). The theme
takes its tokens, fonts and favicon from here, copied by
[`scripts/keycloak-theme.ts`](./scripts/keycloak-theme.ts):

```sh
npm run keycloak-theme   # after changing the tokens in src/index.css, the fonts or public/favicon.svg
```

The [Admin Console](../admin-console/README.md) wears the same tokens, fonts and favicon, copied
into it by [`scripts/admin-console-brand.ts`](./scripts/admin-console-brand.ts). Run it after the
same changes:

```sh
npm run admin-console-brand
```

Tokens are kept in memory only, never in web storage. A reload drops them, so on start the app
asks Keycloak for new ones in a hidden iframe at `/silent-renew`; with a live Keycloak session the
Customer stays signed in, without one they stay signed out. Silent renew refreshes the access token
before it expires. The protected pages send a signed-out Customer to Keycloak and bring them back
to the page they asked for.

## Calling the services

The browser reaches each service at `/api/<service>/…` on the Storefront's own origin: `catalog`,
`inventory`, `cart`, `checkout-pricing`, `order-management`, `search-discovery` and `reviews-ratings`. Everything under `/api/` goes to the
[API gateway](../../platform/api-gateway/README.md), which checks the token and routes it; it sends
no CORS headers. In development the Vite dev proxy forwards `/api` to the gateway's compose host
port, 8000 ([`vite.config.ts`](./vite.config.ts)); in compose, nginx does
([`nginx.conf`](./nginx.conf)).

Every Price, total and Discount is Money: an `amountMinor` in its Currency's Minor unit. The
Storefront shows it by the Minor unit Catalog gives the Currency at `GET /currencies`, fetched once
(`useFormatMoney` in [`src/api/currencies.ts`](./src/api/currencies.ts)), never by the browser's
own currency data: `Intl` gives HUF, IDR, IQD and others 0 digits where ISO 4217 gives 2 or 3, and
an amount shown that way is off a hundredfold (see the
[Catalog README](../../services/catalog/README.md#currencies)). Until the list is in, or for a
Currency it doesn't have, Money shows as a dash rather than a guess.

A Cart holds no prices, so the Cart page prices it from Catalog for display. The checkout page
shows the Checkout Session instead, priced by Checkout; what the Customer pays is what it shows.

The Cart page lists each line with its Variant's thumbnail and name, a quantity stepper and a
remove button, beside an order summary (item count, subtotal, total) that stays in view on wide
screens and moves below the lines on phones. Checkout adds no tax or shipping yet, so the total is
the subtotal. "Empty cart" clears it through `DELETE /cart` once the Customer confirms, then moves
focus to the empty state, since the button that had it is gone.

Checkout shows where the Customer is (Cart → Payment → Done; the Order confirmation shows Done),
and a drawn payment card over a test-card picker: there is nothing to type, since the payment is
mocked. Each test card is a radio named by its card and described by what it does
(`testCards` in [`src/domain/payment.ts`](./src/domain/payment.ts)): **Approve**, **Decline**,
**Insufficient funds** and **Gateway error**, sent as the Payment method `tok_approve`,
`tok_decline`, `tok_insufficient_funds` and `tok_gateway_error`. Approve is chosen to begin with,
and the drawn card shows the chosen one's last four digits.

A declined card (402) shows "Your card was declined" inline, with a line for its `declineReason`;
a payment that didn't go through (502) shows "The payment didn't go through" instead
(`paymentFailure`). Either way the session still holds the items, so the Customer picks a card,
which clears the message, and pays again. Any other failure shows the service's own message.

Arriving there
resumes the Customer's Checkout Session when it still holds exactly their Cart
(`sessionHoldsCart`), and starts a new one otherwise, which replaces the old and releases its
Reservation.
The page shows the session's lines at their held Prices, its price breakdown (subtotal, a Discount
line naming its Coupon when there is one, tax, total: `sessionSummaryRows`), and "held until" the session's expiry with a live `m:ss` countdown
(`sessionCountdown`), all in [`src/domain/checkout.ts`](./src/domain/checkout.ts). The countdown is a
`timer`, so screen readers aren't told every second; the clock time says it once. When the session
expires, or paying finds it over (410 or 404), the page becomes "Your hold expired" with a "Start
again" button, which always starts a new session. Pay is disabled while a payment is in flight.

The price breakdown has a Coupon field. Applying a code, in any case, sends it to Checkout, and the
session it answers with replaces the one shown, so the Discount line, the total and the Pay button
follow at once. A code that doesn't apply (422) says why next to the field, one message per
Promotions `reason` (`couponRejection` in [`src/domain/coupon.ts`](./src/domain/coupon.ts)):
`unknown`, `inactive`, `notYetValid`, `expired`, `belowMinimum` and `currencyMismatch`, with a
general one for a reason it doesn't know. The session keeps whatever Coupon it had. Once one is
applied, the field shows its code with "Remove", which takes it off. Typing clears the message;
Pay waits while a Coupon is being applied, and the Coupon field while a payment is in flight. The
demo Coupon is `WELCOME10`, 10% off.

When Checkout refuses to start a session for Products out of Stock or unknown to Catalog, it names
their Variants, and the page lists their Products (`checkoutProblem`) with a link back to the Cart.

## Error states

No page is ever left blank, and every error a service answers carries a support reference.

- **Error panel.** When a page's data fails to load, the panel takes the content's place: what went
  wrong, "Try again", and "Reference: <Correlation ID>". `failureOf` in
  [`src/api/failure.ts`](./src/api/failure.ts) works both out. A 4xx is in the service's own words, since
  it says what to change; a 5xx says it is on the shop's side, as the service's own "An unexpected
  error occurred." says no more; a request that got no answer says to check the connection. The
  reference is the problem detail's `correlationId`, or the `X-Correlation-Id` response header
  when the body has none (`errorFrom` in [`src/api/http.ts`](./src/api/http.ts)), so support can
  find the request in nginx's, the gateway's and the services' logs. Smaller failures, such as a
  quantity that didn't change or a payment that didn't go through, show the same message and
  reference inline. A request that got no answer has no reference.
- **Where the reference comes from.** In compose, nginx gives every `/api/` request its Correlation
  ID before the gateway sees it ([`nginx.conf`](./nginx.conf)): the caller's when well-formed,
  nginx's own `$request_id` otherwise. The gateway keeps it, so it is the same ID end to end, and
  when the gateway itself is down, nginx's own 502 page still carries it in `X-Correlation-Id` and
  nginx's access line logs it (`correlationId=…`), within 3 seconds rather than a minute
(`proxy_connect_timeout`). A failed 5xx or network read is retried twice
  first, and each attempt is a request with an ID of its own: the reference names the last, and
  the earlier ones failed the same way just before it.
- **Lookups beside the content.** Some pages look things up beside their main content: Variant
  names, images and Prices on the Cart, Checkout, My Orders and Order pages, Stock on the Product
  page, Catalog's Currencies beside the listing's price filter. When one fails the page still shows what it has, falling back as
  before (a Variant ID for its name, "Stock unknown"), and says what didn't load, with its
  reference and "Try again" (`LookupError` in [`Status`](./src/components/Status.tsx), from
  `lookupFailure`). A Category's attribute definitions only order the specs, so when they don't
  load the specs keep the Product's own order and nothing is said.
- **Error boundary.** A page that fails to render shows "Something went wrong" with "Try again" and
  a way back to the Products, inside the header and footer
  ([`ErrorBoundary`](./src/components/ErrorBoundary.tsx)). Moving to another page clears it.
- **Not Found.** An unknown route, a Product Catalog doesn't have and an Order Order Management
  doesn't have show the Not Found page ([`NotFound`](./src/components/NotFound.tsx)). Order
  Management answers 404 for another Customer's Order too, so it is never told apart from one that
  doesn't exist.
- **Expired sign-in.** Silent renew keeps the access token fresh, but when the Keycloak session
  behind it ends, a service answers 401. Any 401, from a page's query or an action such as paying,
  sends the Customer to log in and brings them back to the page they were on (`SigninOnExpiry` in
  [`src/auth/auth.tsx`](./src/auth/auth.tsx)). Meanwhile the panel says "Your sign-in has expired.
  Taking you to log in…", without "Try again". What the page held only in memory, such as the
  chosen test card, starts over.
- **Loading.** Each page shows a skeleton in its own layout while it loads, so nothing jumps when
  the content arrives ([`PageSkeletons`](./src/components/PageSkeletons.tsx); the Product pages
  keep theirs beside them). Screen readers hear "Loading…" once.

To see the panel for real, stop a service and open one of its pages; the reference it shows is
the `correlationId` of the gateway's log lines for that request. Stop the gateway instead and it is
on nginx's access line:

```sh
docker compose stop catalog          # from the repo root; then open http://localhost:8080
docker compose logs api-gateway storefront | grep <reference>
docker compose start catalog
```

## Run it

In compose, nginx serves the production build on http://localhost:8080:

```sh
docker compose up -d --build storefront   # from the repo root
```

For development, run the stack in compose and Vite on http://localhost:5173 (both origins are
allowed redirects of the `storefront` client):

```sh
npm install
npm run dev
```

Sign in as `demo@ecomm.local` / `demo`, or register a new Customer.

## Checks

```sh
npm run typecheck
npm run lint
npm test          # Vitest: failed responses as messages and support references, Money formatting by Catalog's Minor units, Cart pricing, checkout problems, the session countdown and price breakdown, Coupon rejections, test cards and payment failures, quantities, Stock levels, toasts, the search's URL and its Facets as chips and filters, rating stars and summaries and the review form's validation, Product specs by their Category's definitions, Customer initials, Orders and their timeline from the Order Status history, the Orders list's paging, Order Status colours, seed Product images, the Keycloak theme's and the Admin Console's copies of the tokens
npm run test:e2e  # Playwright smoke, search and error-states tests against the running compose stack (`make up`)
```

The smoke test ([`e2e/sprint1.spec.ts`](./e2e/sprint1.spec.ts)) signs in on Keycloak for real,
checks out two Products as the demo Customer with the Approve test card (the "held until" notice and its countdown, their Stock reserved but
still on hand, then the confirmation, the confetti gone under reduced motion, the Order's lines by
Product name, its timeline with Placed then Paid at the times Order Management recorded, its card on
My Orders and, once the demo Customer has more than a page of Orders, the next page, and on-hand Stock down by one), pays with the
Decline card and sees the decline inline with the session kept, then pays the same session with
Approve and sees `PAID`, empties a Cart
through the confirmation, and registers a new Customer. The error-states test
([`e2e/error-states.spec.ts`](./e2e/error-states.spec.ts)) opens unknown route, Product and Order
addresses and sees the Not Found page, answers the Product list with a 500 and sees the panel's
reference and "Try again" recover, fails the Product page's Stock lookup and sees it say so with its
reference, and refuses a token, once on My Orders' data and once on "Add to cart", and sees the
Customer go to Keycloak and come back to the same page. The search test
([`e2e/search.spec.ts`](./e2e/search.spec.ts)) searches the seed by text, chooses a Category and a
storage value and sees the other values still counted, keeps to what is in stock and sorts by
Price, then reloads and opens the URL afresh and sees the same Products in the same order; it
clears filters that match nothing, and pages through, and sees "Out of stock" on, results it stands
in for, since the seed fills less than a page. They need Chromium once:
`npx playwright install chromium`. Set `STOREFRONT_URL` or `KEYCLOAK_URL` to aim it elsewhere.
