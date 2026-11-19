# Kubernetes manifests

The whole stack as Kubernetes manifests, built with Kustomize: one workload per container in the
Compose file ([`infra/docker/compose.yaml`](../docker/compose.yaml)), in the `ecomm` namespace.
Why Kustomize, and what runs where in AWS: [ADR 0012](../../docs/adr/0012-deployed-with-kustomize-to-eks-with-managed-postgres-and-redis-only.md).

```
infra/k8s/
  base/                      the stack, as Compose runs it
    kustomization.yaml       the resources, the patches, and the ConfigMaps made from Compose's files
    stores/                  Postgres, Redis, Couchbase, Mongo, Kafka
    init/                    the one-shot steps, as Jobs
    platform/                Keycloak, Apicurio, Temporal and its UI, Mailpit, the two frontends
    services/                the Spring services, the gateway among them
    observability/           Prometheus, with its own prometheus.yml, Grafana, the Kafka exporter
    patches/                 what every Spring service shares, and no service links for any pod
  overlays/aws/              the AWS environment: RDS, ElastiCache, gp3, GHCR, the ALB's target groups
  compose-parity-check.py    fails if the base has drifted from the Compose file
```

An overlay for an Environment goes in `overlays/<name>/`, with `../../base` as its resource; CI
builds and validates every one.

The [`aws` overlay](./overlays/aws/kustomization.yaml) deletes the base's Postgres and Redis and
makes their Services `ExternalName`s for RDS and ElastiCache, so nothing that connects to them
changes; makes gp3 the default storage class; pulls every image built here from GHCR, through the
`ghcr` pull secret on the namespace's default ServiceAccount; binds the Storefront, Admin Console,
gateway and Keycloak Services to the ALB's target groups; and sets the browser-facing names:
Keycloak's hostname and proxy headers, the frontends' `OIDC_AUTHORITY`, the issuer every Spring
service trusts, and the Storefront URL Notifications and the Support Assistant link to. What
differs per run is in [`environment.env`](./overlays/aws/environment.env) as an example, which
[`replacements.yaml`](./overlays/aws/replacements.yaml) copies into place; `make aws-up` builds a
run's overlay over it with the run's own ([infra/terraform/README.md](../terraform/README.md)).

## Building and checking

The base's ConfigMaps are made from the files Compose mounts (the init scripts, the realm and its
theme, the event schemas, Grafana's dashboards), read where they live, outside the base's
directory, so Kustomize is told it may:

```bash
kustomize build --load-restrictor LoadRestrictionsNone infra/k8s/base
# or kubectl kustomize --load-restrictor LoadRestrictionsNone infra/k8s/base
```

CI's `k8s` job builds the base and every overlay, validates each against Kubernetes 1.35's schemas
with [kubeconform](https://github.com/yannh/kubeconform), the Load Balancer Controller's
`TargetGroupBinding` against [a CRD catalog](https://github.com/datreeio/CRDs-catalog)'s, and runs the parity check on the base.
To run the check locally, with Docker running and `make secrets` done (it reads Compose's resolved
configuration) and [uv](https://docs.astral.sh/uv/) or pipx for its one dependency:

```bash
kustomize build --load-restrictor LoadRestrictionsNone infra/k8s/base \
  | uv run infra/k8s/compose-parity-check.py
```

It checks, for every Compose container but pgAdmin: a workload of the same name (a Job for a
one-shot step, a StatefulSet where Compose mounts a named volume, a Deployment otherwise); the same
image, entrypoint, command and environment; every file Compose mounts, at the same path with the
same content; each secret from the Secret of its name, at the same path, and no other; a memory
limit equal to Compose's cap, and a request equal to the limit; and, for a long-running container,
probes, a Service of the same name, and two replicas for the services built here, one for the rest.
Its docstring lists each rule and the three deliberate differences: pgAdmin isn't in the cluster,
the `catalog-seed` Job isn't in Compose, and Prometheus has a configuration of its own.

So a change to the Compose file is made here too. Kustomize takes no globs, so a new event schema,
Grafana dashboard or theme file is also named in [`base/kustomization.yaml`](./base/kustomization.yaml);
the check says which is missing.

## What the base runs

Every container finds the others by the names Compose gives them, as each is a Service of that
name, so the Spring services run with the `docker` profile their images set, unchanged.

- **Spring services** run two replicas each, Orchestration two workers, with the JVM options and
  caps they have in Compose. [`patches/spring-service.yaml`](./base/patches/spring-service.yaml)
  gives each port 8080 and probes on Spring's `/actuator/health/readiness` and `/liveness`, with up
  to 10 minutes to start. Catalog's replicas don't seed; the `catalog-seed` Job does
  (`com.ecomm.catalog.CatalogSeed`), since two replicas starting together would both seed an
  empty bucket.
- **The frontends** run two replicas each. Their nginx sends `/api/` to
  `api-gateway.ecomm.svc.cluster.local:8080`, named in full as nginx's resolver takes no search
  domains (an overlay that changes the namespace sets `API_GATEWAY` too), and resolved through the cluster's DNS server, which the image reads from the pod's
  `resolv.conf`.
- **StatefulSets with a volume** are what Compose gives a named volume: Postgres, Couchbase, Mongo,
  Kafka and Prometheus (10, 10, 5, 10 and 5 GiB, from the cluster's default storage class, which an
  overlay may set). Redis, Mailpit and Grafana keep nothing on disk, as in Compose, and Keycloak,
  Apicurio and Temporal keep their data in Postgres.
  Postgres and Kafka keep their data in a subdirectory of the volume, since initdb refuses, and
  the broker misreads, a volume's `lost+found`.
- **Keycloak, Apicurio, Temporal and its UI, Mailpit, Prometheus, Grafana and the Kafka exporter**
  run one replica each. Prometheus finds every Spring service's pods through the Kubernetes API,
  allowed to get, list and watch the namespace's pods and nothing else, and scrapes each; Compose's
  configuration names one container per service, and a Service would answer each scrape from
  either replica.
- **Init steps are Jobs**: `postgres-databases`, `couchbase-init`, `kafka-topics`,
  `schema-registry-init`, `temporal-schema`, `temporal-namespace`, `keycloak-realm` and
  `catalog-seed`. Kubernetes starts them all at once, where Compose orders them; one that starts
  before what it needs fails and is retried with a backoff, up to 10 times. Services that start
  before their databases or topics exist exit and are restarted. Each step is idempotent, as
  Compose runs it on every `up`.
- **No pod gets Kubernetes' service links**, the `<SERVICE>_PORT` variables it sets by default:
  Kafka's image would read `KAFKA_PORT` as a broker setting.

Containers Compose leaves uncapped are given limits from the memory readings (README's
[Memory](../../README.md#memory)): Postgres 512 MiB, Redis 128 MiB, Couchbase 1.5 GiB, and the
`postgres-databases` and `couchbase-init` Jobs 64 and 256 MiB. Every container requests the memory
it is limited to, so the scheduler places pods by their caps. CPU is requested, not limited, as
Compose doesn't limit it.

pgAdmin, behind Compose's `tools` profile, isn't in the cluster: with nothing reachable from
outside, it could only be opened through a port-forward anyway, so the port-forward goes to
Postgres instead and pgAdmin, or `psql`, runs on your machine. `kubectl port-forward` listens on a
local port, here 15432 (Compose's Postgres holds 5433), and carries each connection to it through
the Kubernetes API to the Postgres pod, for as long as the command runs:

```bash
kubectl -n ecomm port-forward svc/postgres 15432:5432        # leave running; Ctrl-C closes it
kubectl -n ecomm get secret postgres-password \
  -o jsonpath='{.data.postgres-password}' | base64 -d        # the password
psql -h localhost -p 15432 -U ecomm -d orders                # or a pgAdmin server on localhost:15432
```

Compose's pgAdmin can't be used for this: its `servers.json` names Compose's own `postgres`.

A port-forward reaches pods, so it can't reach a `postgres` Service an overlay points at a database
outside the cluster, as the `aws` overlay does at RDS. Forward to a relay pod instead, which opens
a connection to `postgres` for each one it is given, and delete it after:

```bash
kubectl -n ecomm run pg-relay --image=alpine/socat:1.8.0.3 --port=5432 \
  -- tcp-listen:5432,fork,reuseaddr tcp-connect:postgres:5432
kubectl -n ecomm port-forward pod/pg-relay 15432:5432
kubectl -n ecomm delete pod pg-relay
```

## Secrets

No secret is in a manifest. Each container mounts the Secrets Compose gives it, at the same paths:
one Secret per file `make secrets` writes ([ADR 0011](../../docs/adr/0011-secrets-as-generated-files.md)),
named after the file, with one key of the same name, as
`kubectl create secret generic <name> --from-file=.secrets/<project>/<name>` makes it. A Spring
service's are projected into `/run/secrets` under the property each sets, as Compose's targets
are. The Secrets must exist before the pods can start.

## Applying

The base's Keycloak issues tokens for `http://localhost:8180`, as in Compose, and nothing in it is
reachable from outside the cluster; an environment's overlay gives the hostnames:

```bash
kustomize build --load-restrictor LoadRestrictionsNone infra/k8s/overlays/<env> | kubectl apply -f -
```

A Job's pod template can't be changed, and a changed script changes it, so before applying again
delete the finished Jobs (`kubectl -n ecomm delete job --all`); they run again, as Compose's
one-shot steps do on every `up`.
