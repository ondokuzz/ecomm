# Deployed with Kustomize to EKS, with managed Postgres and Redis only

Sprint 8 runs the whole stack in AWS for a load test and a fault drill, then tears it down. The environment exists for a test run, not to serve customers, so it is shaped by what the test needs and what it costs per hour.

- **Kustomize, not Helm.** [`infra/k8s/base`](../../infra/k8s/README.md) holds one workload per container in the Compose file, with the same image, environment, secrets mounted as files at the same paths, the same scripts and configuration files, and memory limits equal to Compose's caps. Overlays change what differs per environment; the `aws` overlay (#140) will be the first. The stack has one shape, so there is nothing to template: Kustomize's patches cover what an overlay changes, with no templating language to learn or debug.
- **The Compose file stays the source.** The manifests are written to match it, and CI's [`compose-parity-check.py`](../../infra/k8s/compose-parity-check.py) fails if they drift: a container without a workload, a different image, variable, cap, secret or mounted file. Containers find each other by the same names, so every service's `docker` profile runs unchanged in the cluster.
- **Managed Postgres and Redis only.** RDS and ElastiCache hold the data that can't be rebuilt from a topic: Orders, Payments, Stock and its ledger, Carts and Checkout Sessions. The base runs Postgres and Redis as Compose does; the `aws` overlay is to replace them with Services of the same names that point at RDS and ElastiCache. Everything else runs in the cluster, as StatefulSets with volumes where Compose has a named volume (Postgres, Kafka, Mongo, Couchbase, Prometheus) and as Deployments otherwise. Sprint 8's spec (#125) had Keycloak, Temporal, Apicurio, Mailpit and Grafana as StatefulSets with volumes too; none keeps anything on disk in Compose (Keycloak, Temporal and Apicurio keep their data in Postgres), so a volume would hold nothing. The storage class, gp3 in AWS, is the overlay's.
- **Stateless services run two replicas**, so the fault drill's deleted pod doesn't fail requests; Orchestration runs two workers. Keycloak, Temporal, Apicurio, Mailpit and the stores run one, as in Compose.
- **Init steps are Jobs.** Each of Compose's one-shot containers (databases, topics, schemas, Temporal's schema and namespace, realm sync) is a Job, and so is Catalog's seed, which Catalog does as it starts in Compose: two replicas starting together would both seed an empty bucket.

## Considered Options

- **Helm.** Rejected: one shape of stack needs no templating, and a chart's values would be a second description of what Compose already says.
- **MSK for Kafka.** Rejected for a test run: its cost and set-up time buy nothing one broker doesn't. It becomes worth it when Kafka needs more than one broker, for availability or throughput.
- **DocumentDB or MongoDB Atlas for Mongo.** Rejected likewise; Mongo only holds projections, rebuilt from their topics. Worth it when the projections outgrow one node.
- **Couchbase Capella for Catalog.** Rejected likewise. Worth it when Catalog needs Couchbase's replication across nodes.
- **Everything in the cluster, RDS and ElastiCache included.** Rejected: the data that can't be rebuilt belongs in stores AWS backs up and patches, as it would in production.

## Consequences

- A change to the Compose file needs the same change in `infra/k8s/base`, or CI fails, naming the difference. Kustomize takes no globs, so a new event schema, dashboard or theme file is also named in the base's `kustomization.yaml`.
- The base reads the files Compose mounts where they live, outside its directory, so it is built with `--load-restrictor LoadRestrictionsNone`.
- Kubernetes runs the Jobs at once, where Compose orders them by `depends_on`; a Job that starts before what it needs fails and is retried with a backoff, and a service that starts first exits and is restarted. Bringing the stack up takes some restarts, never a hand-run step.
- With two replicas, each Spring service's sweeps and Catalog's Outbox relay run twice. Consumers apply an event only if it is newer than what they hold, as they must for Kafka's redelivery anyway; the duplicate-delivery tests (#133 to #135) are to prove it of every consumer group.
- Prometheus finds the services' pods through the Kubernetes API, since a Service would answer each scrape from either replica. The dashboards sum over instances, so they read as they do locally.
