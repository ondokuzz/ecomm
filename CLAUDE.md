# Ecomm Platform

Start with `README.md`, `GLOSSARY-MAP.md`, `GLOSSARY.md`, and `docs/roadmap.md`.

A sprint's definition-of-done pass proves a stack from the sprint before upgrades: `docs/agents/upgrade-check.md`.

A memory reading, before and after a change that adds a container, or a sprint's: `docs/agents/memory-reading.md`.

Rebuilding images, or running `./gradlew check`, beside the running stack: `README.md#memory` first; on 8 GB neither fits beside it.

## Agent skills

### Issue tracker

GitHub Issues on `ondokuzz/ecomm`, via the `gh` CLI. See `docs/agents/issue-tracker.md`.

### Triage labels

The five default labels: `needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, `wontfix`. See `docs/agents/triage-labels.md`.

### Domain docs

Multi-context: a root `GLOSSARY-MAP.md` plus a shared root `GLOSSARY.md`. System ADRs live in `docs/adr/` and context ADRs in `services/<context>/docs/adr/`. See `docs/agents/domain.md`.
