# Ecomm Platform

Start with `README.md`, `CONTEXT-MAP.md`, `CONTEXT.md`, and `docs/roadmap.md`.

## Environment

Run `source ~/.zshrc` before docker, java, or gradle commands. That puts Docker Desktop, Temurin JDK 21, and Gradle 9.7.1 on PATH.

## Agent skills

### Issue tracker

GitHub Issues on `ondokuzz/ecomm`, via the `gh` CLI. See `docs/agents/issue-tracker.md`.

### Triage labels

The five default labels: `needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, `wontfix`. See `docs/agents/triage-labels.md`.

### Domain docs

Multi-context: a root `CONTEXT-MAP.md` plus a shared root `CONTEXT.md`. System ADRs live in `docs/adr/` and context ADRs in `services/<context>/docs/adr/`. See `docs/agents/domain.md`.
