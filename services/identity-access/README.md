# Identity & Access

Keycloak, not a Spring service. See [ADR 0001](./docs/adr/0001-keycloak-as-identity-provider.md) for why.

The `ecomm` realm is defined in [`realm/realm-ecomm.json`](./realm/realm-ecomm.json). Compose mounts it into Keycloak, which imports it on first start (`start-dev --import-realm`) and keeps it in the Postgres database `keycloak`. The import skips a realm that already exists, so to pick up changes to the file, delete the realm in the admin console (http://localhost:8180, `admin` / `admin`) or drop the `postgres-data` volume, then restart Keycloak.

## What the realm holds

| | |
|---|---|
| Realm roles | `CUSTOMER`, `STAFF`, `CHECKOUT` |
| Self-registration | on; new users get `CUSTOMER` through `default-roles-ecomm` |
| Access-token lifespan | 15 minutes |
| SSL | not required (`sslRequired: none`), **for local development only**: Docker can present requests from the host with a public source IP, which the default (`external`) would refuse over plain HTTP |
| `storefront` | public client, Authorization Code + PKCE S256, redirects `http://localhost:8080/*` and `http://localhost:5173/*` (and their `127.0.0.1` equivalents) |
| `admin-console` | public client, reserved for Sprint 2 (no redirect URIs yet) |
| `dev-cli` | public client with the password grant, **for local development and tests only** |
| `checkout` | confidential client with client credentials only; its service account holds `CHECKOUT`. Secret `checkout-dev-secret`, **for local development only** |
| Seeded users | `demo@ecomm.local` / `demo` (CUSTOMER), `staff@ecomm.local` / `staff` (STAFF) |

## Login theme

The `ecomm` realm's login, registration and error pages wear the Storefront's brand through the
`ecomm` login theme in [`themes/ecomm/`](./themes/ecomm). It extends Keycloak's stock `keycloak.v2`
theme with stylesheets only, so the templates, messages and scripts stay Keycloak's own and an
upgrade needs no template merge:

| | |
|---|---|
| `resources/css/ecomm.css` | Written by hand: maps PatternFly onto the Storefront's tokens and restyles the header, card, fields, buttons and messages |
| `resources/css/tokens.css`, `resources/css/fonts.css`, `resources/fonts/`, `resources/img/favicon.svg` | Generated from the Storefront's design tokens, fonts and favicon. Don't edit them; after changing those in the Storefront, run `npm run keycloak-theme` in `frontend/storefront` (its tests fail until you do) |

Dark mode follows the operating system, as in the Storefront, through the class keycloak.v2 puts on
the page while the realm's dark mode is on (the default). The master realm, and so the admin
console, keeps Keycloak's own theme.

`ecomm.css` styles keycloak.v2's PatternFly 5 classes and variables. If a Keycloak upgrade moves the
login pages to another PatternFly version, the pages fall back to the stock look and the rules need
porting; check the login, register and error pages after upgrading.

Compose mounts the theme at `/opt/keycloak/themes/ecomm`, and the realm file sets it as the
`loginTheme`, so a fresh stack shows it. `start-dev` doesn't cache themes: edits to the CSS show on
the next page load.

A realm imported before the theme existed keeps the stock one, since the import skips an existing
realm. Set it on the running realm, either in the admin console (Realm settings → Themes → Login
theme → `ecomm`) or with:

```sh
docker compose exec keycloak /opt/keycloak/bin/kcadm.sh update realms/ecomm -s loginTheme=ecomm \
  --no-config --server http://localhost:8080 --realm master --user admin --password admin
```

## Get a token by hand

```sh
curl -s http://localhost:8180/realms/ecomm/protocol/openid-connect/token \
  -d grant_type=password -d client_id=dev-cli \
  -d username=demo@ecomm.local -d password=demo | jq -r .access_token
```

The token's `iss` is `http://localhost:8180/realms/ecomm`, `sub` is the Customer ID, and `realm_access.roles` holds the realm roles.

Checkout calls other services' internal endpoints with a token of its own
([ADR 0002](./docs/adr/0002-service-identity-by-client-credentials.md)). To get one by hand:

```sh
curl -s http://localhost:8180/realms/ecomm/protocol/openid-connect/token \
  -d grant_type=client_credentials -d client_id=checkout \
  -d client_secret=checkout-dev-secret | jq -r .access_token
```

Its `sub` is the `checkout` service account, not a Customer, and `realm_access.roles` is `["CHECKOUT"]`.
