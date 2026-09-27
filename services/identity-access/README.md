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
| `storefront` | public client, Authorization Code + PKCE S256, redirects `http://localhost:8080/*` and `http://localhost:5173/*` |
| `admin-console` | public client, reserved for Sprint 2 (no redirect URIs yet) |
| `dev-cli` | public client with the password grant, **for local development and tests only** |
| `checkout` | confidential client with client credentials only; its service account holds `CHECKOUT`. Secret `checkout-dev-secret`, **for local development only** |
| Seeded users | `demo@ecomm.local` / `demo` (CUSTOMER), `staff@ecomm.local` / `staff` (STAFF) |

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
