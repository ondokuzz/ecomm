# Coding standards

Read at review time. Formatting, lint rules, types and the hexagonal layering are left to the
tools (Prettier and oxlint in each frontend app's `lint`, Spotless and `HexagonalRules` in
`./gradlew check`); these are the calls no tool makes.

## Domain language

Names in code, tests and docs use the glossary's terms, as [`docs/agents/domain.md`](./docs/agents/domain.md)
sets out: a Rating summary is not a rating, a Customer is not a user.

## Tests

- A test asserts what can be observed from outside: an HTTP status or body, a later read, what
  appears on a topic, what the page shows. Internal classes, tables and collections are not
  observed.
- An end-to-end test makes the data it needs (a Customer through Keycloak's admin API, a Product,
  a Coupon, a Campaign) and deletes it in `afterEach` or `finally`, so a failed run leaves the
  stack as it found it. Orders can't be deleted and stay.
- In the Storefront's suite, a test that checks out belongs in `e2e/smoke.spec.ts`, whose tests
  run one at a time, since its first test compares Stock before and after checkout.

## Design

A pattern, abstraction or option earns its place by a requirement that forces it, named in the
spec, an ADR or the change's own reasoning. When an ADR's decision changes, the ADR is revised in
place with a dated note.

## Docs

A README, roadmap or ADR that describes the code changes with it, in the same change, and says
only what the code does: a count, a file name, a claim that "every test that does X is in Y" is
checked against the diff.
