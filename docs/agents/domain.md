# Domain Docs

How the engineering skills should consume this repo's domain documentation when exploring the codebase.

## Before exploring, read these

- **`CONTEXT-MAP.md`** at the repo root lists the fifteen bounded contexts, where each one lives under `services/`, and how they relate.
- **`CONTEXT.md`** at the repo root is currently the one **shared** glossary for all contexts. A context earns its own `services/<context>/CONTEXT.md` only once its terms diverge from the shared one; `CONTEXT-MAP.md` then points to it. Read whichever `CONTEXT.md` files are relevant to the topic.
- **`docs/adr/`** holds system-wide decisions. Read the ADRs that touch the area you're about to work in.
- **`services/<context>/docs/adr/`** holds context-scoped decisions (e.g. `services/catalog/docs/adr/`). Check it for the context you're working in.
- **`docs/roadmap.md`** is the sprint-by-sprint delivery plan, which says what's in scope when.

If any of these files don't exist, **proceed silently**. Don't flag their absence, and don't suggest creating them upfront. The `/domain-modeling` skill (reached via `/grill-with-docs` and `/improve-codebase-architecture`) creates them lazily when terms or decisions actually get resolved.

## File structure

Multi-context repo:

```
/
├── CONTEXT-MAP.md                     ← contexts, locations, relationships
├── CONTEXT.md                         ← shared glossary (until a context diverges)
├── docs/
│   ├── adr/                           ← system-wide decisions
│   └── roadmap.md
└── services/
    ├── catalog/
    │   ├── (CONTEXT.md)               ← only once its terms diverge
    │   └── docs/adr/                  ← context-specific decisions
    └── order-management/
        └── docs/adr/
```

## Use the glossary's vocabulary

When your output names a domain concept (in an issue title, a refactor proposal, a hypothesis, a test name), use the term as defined in `CONTEXT.md`. Don't drift to synonyms the glossary explicitly avoids (e.g. "Customer", not "User"; "Cart", not "Basket").

If the concept you need isn't in the glossary yet, that's a signal: either you're inventing language the project doesn't use (reconsider) or there's a real gap (note it for `/domain-modeling`).

## Keep the glossary current

When implementation surfaces a new domain term or sharpens an existing one, update `CONTEXT.md` in the same change using its existing format (term, definition, _Avoid_). If a relationship between contexts changes, update `CONTEXT-MAP.md` too.

## Flag ADR conflicts

If your output contradicts an existing ADR, surface it explicitly rather than silently overriding:

> _Contradicts ADR-0002 (CQRS + event sourcing scoped to four contexts), but worth reopening because…_
