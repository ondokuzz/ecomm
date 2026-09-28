# Storefront

The Customer-facing React app: browse Products by category, see a Product's Price and Stock, keep a
Cart, check out with a mock payment, and follow Orders and their Order Status. Built with Vite,
React, TypeScript, React Router and TanStack Query.

## Pages

| Path | | Login |
|---|---|---|
| `/?category=` | Products, filtered by category | no |
| `/products/{sku}` | A Product with its Price and Stock; add it to the Cart | no (adding needs it) |
| `/cart` | The Cart, priced from Catalog's current Prices | yes |
| `/checkout` | The Cart once more, and a mock "Pay" button | yes |
| `/orders/{id}` | An Order and its Order Status; after checkout, the confirmation | yes |
| `/orders` | My Orders, newest first | yes |

## Design

The look lives in [`src/index.css`](./src/index.css): design tokens (colours, gradients, fonts, spacing,
corner radii, shadows) as CSS custom properties on `:root`, redefined for dark mode, which follows
`prefers-color-scheme`. Everything else reads the tokens rather than one-off values. The fonts, Inter,
and Bricolage Grotesque for headings, are bundled through Fontsource rather than loaded from a CDN.

The pages build from the shared components in [`src/components/ui`](./src/components/ui): Button,
Card, Badge, Input, QuantityStepper, Skeleton and Icon. The header's account menu and the
small-screen menu are native `popover` elements, so the browser handles closing them on Escape or an
outside click.

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
type. Catalog loads the seed only into an empty bucket, so a stack seeded before that change needs
`make seed-reset` to pick up the new paths (which also drops every Cart, Order and Payment); until
then every Product shows the placeholder.

Vite serves `public/` as is in development, and the build copies it into `dist/`, which nginx
serves in compose with a one-day cache (the names don't change when the images are regenerated). A
missing image is a 404 there, not the app's `index.html`; Vite answers it with `index.html`, which
fails to load as an image just the same. A test (`npm test`) checks that every seed
Product has its image at the path Catalog returns, under 50 KB.

## Auth

`oidc-client-ts` (through `react-oidc-context`) signs the Customer in with Authorization Code +
PKCE against the realm's public `storefront` client. Login and registration (`prompt=create`) both
happen on Keycloak's hosted pages.

Tokens are kept in memory only, never in web storage. A reload drops them, so on start the app
asks Keycloak for new ones in a hidden iframe at `/silent-renew`; with a live Keycloak session the
Customer stays signed in, without one they stay signed out. Silent renew refreshes the access token
before it expires. The protected pages send a signed-out Customer to Keycloak and bring them back
to the page they asked for.

## Calling the services

The browser reaches each service at `/api/<service>/…` on the Storefront's own origin, since the
services send no CORS headers: `catalog`, `inventory`, `cart`, `checkout-pricing` and
`order-management`. In development the Vite dev proxy forwards them to the services' compose host
ports ([`vite.config.ts`](./vite.config.ts)); in compose, nginx does ([`nginx.conf`](./nginx.conf)).

A Cart holds no prices, so the Cart and checkout pages price it from Catalog for display.
Checkout prices it again itself; what the Customer pays is what the Order shows.

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
npm test          # Vitest: Money formatting, Cart pricing, quantities, Customer initials, seed Product images
npm run test:e2e  # Playwright smoke test against the running compose stack (`make up`)
```

The smoke test ([`e2e/sprint1.spec.ts`](./e2e/sprint1.spec.ts)) signs in on Keycloak for real,
checks out two Products as the demo Customer and registers a new Customer. It needs Chromium once:
`npx playwright install chromium`. Set `STOREFRONT_URL` or `KEYCLOAK_URL` to aim it elsewhere.
