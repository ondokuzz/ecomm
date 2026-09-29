# Storefront

The Customer-facing React app: browse Products by category, see a Product's Price and Stock, keep a
Cart, check out with a mock payment, and follow Orders and their Order Status. Built with Vite,
React, TypeScript, React Router and TanStack Query.

## Pages

| Path | | Login |
|---|---|---|
| `/?category=` | Products, filtered by category | no |
| `/products/{sku}` | A Product with its image, Price, Stock and specs; add it to the Cart | no (adding needs it) |
| `/cart` | The Cart, priced from Catalog's current Prices; change quantities, remove lines or empty it | yes |
| `/checkout` | The order summary, and a mock payment card with a "Pay" button | yes |
| `/orders/{id}` | An Order: its Order Status timeline, its lines and summary; after checkout, the confirmation first | yes |
| `/orders` | My Orders as cards, newest first | yes |

## Design

The look lives in [`src/index.css`](./src/index.css): design tokens (colours, gradients, fonts, spacing,
corner radii, shadows) as CSS custom properties on `:root`, redefined for dark mode, which follows
`prefers-color-scheme`. Everything else reads the tokens rather than one-off values. The fonts, Inter,
and Bricolage Grotesque for headings, are bundled through Fontsource rather than loaded from a CDN.

The pages build from the shared components in [`src/components/ui`](./src/components/ui): Button,
Card, Badge, Input, QuantityStepper, Skeleton, Icon, Toast and ConfirmDialog. The header's account menu and the
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

## Orders

After checkout the Order page opens with a confirmation: a big check, confetti that falls once, the
Order's reference, item count and total, and "Continue shopping" / "View my orders". The confetti
is decorative and hidden outright when the Customer prefers reduced motion.

An Order is shown by its Order reference (`#3F2A9C1B`, the start of its ID; the full ID is in its
summary). Its page has an Order Status timeline, Placed → Paid → Fulfilled → Shipped → Delivered
with the current step marked; a cancelled Order ends at Cancelled after Placed, in red, since its
Order Status doesn't say whether it was paid, and a returned one at Returned after Delivered, in
amber. Its lines name their Products and show their thumbnails, looked up from Catalog, and fall back
to the Variant ID when Catalog no longer has the Product.

My Orders lists them as cards: reference, date, the first three Products' thumbnails (and how many
more), item count, total and Order Status badge. Each Order Status badge has a colour of its own
([`orderStatusTones`](./src/components/orderStatusTones.ts)), always with its name, so colour is
never the only cue.

## Product images

A Product shows its first image through [`ProductImage`](./src/components/ProductImage.tsx). When it
has no images, or the file fails to load, a placeholder takes its place, so there is never a
broken-image icon. The image's `alt` text is the Product's name.

The seed Products' images are generated artwork, not photographs, so there is nothing to license: an
SVG of a phone, laptop, headphones, earbuds or speaker, in the Product's colour on a backdrop in its
brand's colour from the palette. They live in [`public/images/products/`](./public/images/products)
at the paths Catalog's seed gives them (`/images/products/<sku-lowercase>/front.svg`), and are
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
Product has its image at the path Catalog returns, under 50 KB.

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

Tokens are kept in memory only, never in web storage. A reload drops them, so on start the app
asks Keycloak for new ones in a hidden iframe at `/silent-renew`; with a live Keycloak session the
Customer stays signed in, without one they stay signed out. Silent renew refreshes the access token
before it expires. The protected pages send a signed-out Customer to Keycloak and bring them back
to the page they asked for.

## Calling the services

The browser reaches each service at `/api/<service>/…` on the Storefront's own origin: `catalog`,
`inventory`, `cart`, `checkout-pricing` and `order-management`. Everything under `/api/` goes to the
[API gateway](../../platform/api-gateway/README.md), which checks the token and routes it; it sends
no CORS headers. In development the Vite dev proxy forwards `/api` to the gateway's compose host
port, 8000 ([`vite.config.ts`](./vite.config.ts)); in compose, nginx does
([`nginx.conf`](./nginx.conf)).

A Cart holds no prices, so the Cart and checkout pages price it from Catalog for display.
Checkout prices it again itself; what the Customer pays is what the Order shows.

The Cart page lists each line with its Product's thumbnail and name, a quantity stepper and a
remove button, beside an order summary (item count, subtotal, total) that stays in view on wide
screens and moves below the lines on phones. Checkout adds no tax or shipping yet, so the total is
the subtotal. "Empty cart" clears it through `DELETE /cart` once the Customer confirms, then moves
focus to the empty state, since the button that had it is gone.

Checkout shows where the Customer is (Cart → Payment → Done; the Order confirmation shows Done),
the summary with its lines, and a
drawn payment card: there is nothing to type, since the payment is mocked. When Checkout refuses a
Cart for Products out of Stock or unknown to Catalog, it names their Variants, and the page names
their Products (`checkoutProblem`, in [`src/domain/checkout.ts`](./src/domain/checkout.ts)).

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
npm test          # Vitest: Money formatting, Cart pricing, checkout problems, quantities, Stock levels, toasts, category chips, Customer initials, Orders and their timeline, Order Status colours, seed Product images, the Keycloak theme's copy of the tokens
npm run test:e2e  # Playwright smoke test against the running compose stack (`make up`)
```

The smoke test ([`e2e/sprint1.spec.ts`](./e2e/sprint1.spec.ts)) signs in on Keycloak for real,
checks out two Products as the demo Customer (the confirmation, the confetti gone under reduced
motion, the Order's lines by Product name, its timeline and its card on My Orders), empties a Cart
through the confirmation, and registers a new Customer. It needs Chromium once:
`npx playwright install chromium`. Set `STOREFRONT_URL` or `KEYCLOAK_URL` to aim it elsewhere.
