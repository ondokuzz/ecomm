# Admin Console

The back-office React app where Staff manage the Catalog: Products with their Variants, Prices and
Stock, and Categories with their attribute definitions. Built on the Storefront's stack:
Vite, React, TypeScript, React Router and TanStack Query, with `oidc-client-ts`.

## Pages

| Path | |
|---|---|
| `/products` | Every Product in a table: name, SKU, Category, Variant count and lowest Price. Filter by Category (`?category=`) and search names and SKUs (`?q=`). Edit or delete each |
| `/products/new` | A new Product, in the Category `?category=` names if any |
| `/products/{sku}` | A Product, its Variants and their Stock. The SKU and Category can't change here |
| `/categories` | Every Category in a table: name, slug, attribute definitions (Variant axes highlighted), Product count. Edit or delete each |
| `/categories/new` | A new Category: name, slug and attribute definitions |
| `/categories/{slug}` | A Category's name and attribute definitions. The slug can't change, since Products name their Category by it |

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

The Variants table has one row per Variant:

- **Variant ID.** Typed for a new row, usually the SKU for the first. A saved Variant's ID is shown
  read-only, since Carts, Orders and Stock name it.
- **One value per Variant axis.**
- **Price**, typed as a decimal such as `799.00` in the Product's one currency (`EUR` unless
  changed) and sent as Money, `{"amountMinor": 79900, "currency": "EUR"}`. `moneyOf` and
  `decimalOf` in [`src/domain/money.ts`](./src/domain/money.ts) convert digit by digit, so no
  floating-point rounding creeps in. A decimal with more fraction digits than the currency has,
  such as `799.001`, or with a thousands separator, is refused beside the field before anything is
  sent.
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
the counts still to set. `productRequest` and `stockChanges` in
[`src/domain/product.ts`](./src/domain/product.ts) turn the form into those requests.

Catalog keeps every Variant a Product has had until the Product is deleted. Removing a saved row
and saving is refused with a 409 naming the Variant IDs it must keep, shown above the form.

Deleting a Product goes through a ConfirmDialog, and takes its Variants with it. Inventory keeps
their Stock rows, which nothing reads once Catalog no longer has the Variants.

## Validation errors

Catalog checks every write. A 400 names each offending field in its problem detail's `errors`,
such as a Category's `attributes[1].values` or a Product's `attributes.screen`,
`variants[1].axisValues.storage` or, for two Variants alike, `variants[2].axisValues` (see the
[Catalog README](../../services/catalog/README.md#categories-and-attribute-definitions)).
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

The browser reaches Catalog at `/api/catalog/…` and Inventory at `/api/inventory/…` on the
console's own origin. Everything under
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
npm test          # Vitest: failed requests as messages and references, field errors from problem details, the STAFF role in an access token, a Category and a Product to and from their editor's form, decimals to and from Money
npm run build
npm run test:e2e  # Playwright against the running compose stack (`make up`)
```

The Playwright test ([`e2e/products.spec.ts`](./e2e/products.spec.ts)) signs in on Keycloak as
`staff@ecomm.local`. It creates a Product in `phones` with two Variants and sets their Stock, then
checks Catalog and Inventory through their APIs. On the Storefront it picks the second Variant and
sees its Price and Stock. Finally it deletes the Product through the ConfirmDialog, and through
Catalog's API if the test failed before that. Each run's SKU is new, since the Variants' Stock rows
stay in Inventory. It runs against the console on 8090; set `ADMIN_CONSOLE_URL` to use Vite's
5174, and `STOREFRONT_URL` or `KEYCLOAK_URL` for other hosts. Install Chromium once with
`npx playwright install chromium`.
