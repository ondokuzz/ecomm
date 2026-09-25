# Ecomm Platform

A from-scratch e-commerce platform, architected as fifteen bounded contexts across five parallel team tracks, planned over eight two-week sprints for a small (4-5 person) engineering team. This repo currently holds architecture docs and an empty service scaffold — no application code yet.

## Layout

- `services/` — one directory per bounded context (see `CONTEXT.md` for the domain terms, `docs/adr/` for why each is shaped the way it is)
- `frontend/storefront`, `frontend/admin-console` — the two customer/staff-facing React apps
- `platform/` — shared, cross-service concerns: the event-schema library and the hexagonal-architecture service starter template
- `infra/terraform` — infrastructure-as-code for the eventual AWS deployment

## Docs

- [`CONTEXT-MAP.md`](./CONTEXT-MAP.md) — the fifteen bounded contexts, how they relate, and where their docs live
- [`CONTEXT.md`](./CONTEXT.md) — the shared domain glossary
- [`docs/roadmap.md`](./docs/roadmap.md) — the sprint-by-sprint delivery plan
- [`docs/adr/`](./docs/adr/) — system-wide architecture decision records
- Context-specific ADRs live inside the service they belong to, e.g. [`services/catalog/docs/adr/`](./services/catalog/docs/adr/)
