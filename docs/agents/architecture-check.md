# The architecture check

Every sprint's definition-of-done pass brings [`docs/architecture.md`](../architecture.md) up to
date with what the sprint built. Tickets update `GLOSSARY-MAP.md`, the source of truth for the
relationships, as they go. The diagrams are redrawn once, here, so they don't drift for a sprint
or more, as they did from Sprint 6 to Sprint 8.

## Steps

1. List what changed since the previous sprint's definition-of-done commit
   (`git log --oneline --grep "definition of done"`):

   ```sh
   FROM=<commit>
   git diff $FROM -- GLOSSARY-MAP.md infra/docker/consumer-groups platform/event-schemas/schemas \
     infra/docker/compose.yaml platform/api-gateway/src/main/resources/application.yml
   ```

2. Check each view against its source, and redraw what differs:

   | View | Check it against |
   |---|---|
   | Requests | The gateway's routes in `platform/api-gateway/src/main/resources/application.yml`, including its public reads, webhooks and signed links; each service's store and any new container in `compose.yaml` |
   | Calls between contexts | `GLOSSARY-MAP.md`'s Relationships that are calls, not events: internal endpoints, the Sagas' commands, and reads made with a Customer's own token |
   | Events | Every topic in `platform/event-schemas/schemas`, its publisher, and every group in `infra/docker/consumer-groups` |
   | The Sagas' sequences | Each Saga's workflow in `services/orchestration`: a step added, moved or compensated differently |

3. Update the text around them: the intro's "as of Sprint N", each section's prose where a
   diagram changed, and "Not built yet" for what the next sprint plans in `docs/roadmap.md`.
4. Check that every diagram renders: GitHub's preview of the file, or
   `npx @mermaid-js/mermaid-cli` if it is allowed.
5. Commit as `Architecture: <what changed>, as of Sprint N`, before the definition-of-done
   commit, and tick the item in the sprint's definition-of-done ticket.

A sprint that changed no relationship, route, topic, store or Saga still gets the intro's sprint
number updated, so the file says it was checked.
