# Keycloak as the identity provider

Identity & Access is Keycloak (realm `ecomm`), not a hand-rolled identity service. It speaks standard OIDC, so the Storefront logs in with Authorization Code + PKCE and every service validates JWTs with stock Spring Security. It is free and open source, and Staff login for the Admin Console (Sprint 2) is a second client in the same realm rather than new code. The realm is kept as code in [`realm/realm-ecomm.json`](../../realm/realm-ecomm.json) and imported when Keycloak starts.

## Considered Options

- A hand-rolled identity service (Spring Boot + Spring Authorization Server, or plain JWT minting) was rejected. It means writing and securing registration, password storage, login pages, and token issuance ourselves, and none of that is this platform's domain.

## Consequences

- One more container to run (Keycloak, persisted in the shared Postgres as database `keycloak`).
- Tokens carry the browser-facing issuer `http://localhost:8180/realms/ecomm`, while services inside Compose fetch keys from `http://keycloak:8080`. So services configure the JWK set URI and the expected issuer separately rather than relying on issuer discovery.
- A Customer's identity lives in Keycloak, and the Customer ID other contexts store is the token's `sub`. No context keeps its own Customer table for authentication.
