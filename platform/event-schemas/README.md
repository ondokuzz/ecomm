# Event schemas

The JSON Schema of every integration event, one per topic, in `schemas/<topic>.json`. This is
the only place an event's shape is defined ([ADR 0002](../../docs/adr/0002-ledgers-and-outboxes-not-event-sourcing.md)).
Changing an event means changing its schema here, in a reviewed pull request.

| Topic | Key | Published by |
|---|---|---|
| `order-management.order` | `orderId` | Order Management |
| `inventory.stock` | `variantId` | Inventory |
| `catalog.product` | `sku` | Catalog |
| `catalog.category` | `slug` | Catalog |
| `payment.payment` | `paymentId` | Payment |

## The shape of an event

Topics are named `<context>.<aggregate>`. Each carries one event type: a state-carrying snapshot of
its aggregate, keyed by the aggregate's ID. Every event has:

- `eventId`: a UUID, the same on every delivery of the same event;
- `occurredAt`: when it was published, in the transaction that made the change;
- the aggregate's ID, named after it, such as `orderId`;
- `version`: the aggregate's version, which goes up by at least one with every change. A consumer
  ignores an event that isn't newer than what it holds;
- `change`: why it was published, from the topic's own set, such as `PLACED`, `STATUS_CHANGED` or
  `BACKFILLED` for an Order;
- the aggregate's state, under its name, such as `order`.

An aggregate that leaves, such as a Product removed from the Catalog, is published as a last
snapshot that says so (`removed: true`), never as a tombstone. Topics are compacted, so replaying
one yields every aggregate's latest event. The Correlation ID of the request that caused an event
travels in its `X-Correlation-Id` header, not in the event.

## Changing a schema

Producers validate every event against the latest registered schema and refuse one that doesn't
match. Consumers read tolerantly: they ignore fields they don't know and validate nothing. A change
must therefore be **backward compatible**: data valid under the old schema must be valid under the
new one. The registry checks this, as does CI.

- **Allowed:** adding an optional field; adding a value to an enum; relaxing a constraint.
- **Refused:** making a field required, adding a required field, removing a field, removing an enum
  value, or narrowing a type.

Every object in a schema sets `additionalProperties: false`, and `EventSchemasTest` fails if one
doesn't. That is what makes removing a field incompatible: in an open object, an old event that
still had the field would stay valid. The registry also refuses a new optional field in an open
object, since an old event might have carried that field with another type. A map whose keys are data, such as a Product's `attributes`, gives its values' schema in
`additionalProperties` instead.

A change that can't be made compatible is a new topic, with consumers moved over to it.

`src/test/resources/examples/` holds one example event per topic, which `EventSchemasTest` checks
against its schema. Update it with the schema.

## Registering the schemas

`register.sh` registers each schema in Apicurio as the artifact `<topic>-value` in the `default`
group. That is the artifact a producer's serializer looks up for its topic. Each artifact has a
BACKWARD compatibility rule. A schema that is already the latest version is left alone. A changed
one becomes a new version, or is refused with the reason, and the script exits non-zero.

It runs in three places:

- **Compose:** `schema-registry-init` runs it on every `make up`, against the stack's registry. A
  refused schema fails `make up`.
- **CI:** the `event-schemas` job runs it against a throwaway registry, first with the schemas from
  before the change, then with the change's own.
- **By hand:** against the running stack:

  ```sh
  REGISTRY_URL=http://localhost:8088/apis/registry/v3 platform/event-schemas/register.sh
  ```

Producers never register schemas themselves. A service's tests register the schemas they need with
`EventBackbone.registerSchemaOf(topic)` from `service-commons`' test fixtures, which reads them from
this module's classpath (`event-schemas/<topic>.json`). Add `testImplementation(project(":platform:event-schemas"))`
to use it.

Kafka's `kafka-topics` step creates a topic for each schema file here, so a new topic is one new
schema.
