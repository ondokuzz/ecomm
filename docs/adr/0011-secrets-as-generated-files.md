# Secrets are files, generated per stack and mounted per container

Until Sprint 5 every secret was a dev constant in plain sight: the Keycloak client secrets, Payment's webhook secret, and Postgres', Couchbase's, Keycloak's, Grafana's and pgAdmin's admin passwords were written into the Compose file and the realm file, and so were the same on every stack. Now no secret is in the repo, an image or Compose's resolved configuration.

- **Generated per stack.** `make secrets` ([`infra/docker/secrets.sh`](../../infra/docker/secrets.sh)), which `make up` runs first, writes each secret the stack lacks as a file in `.secrets/<Compose project>/`. A new one is 32 random bytes, URL-safe. A file that is there is never overwritten, so the stack keeps its secrets across restarts, and rotating one is deleting or rewriting its file by hand (the root README says how).
- **Mounted per container.** Each is a Compose secret, and each container lists only the ones it needs. Compose mounts each as a file in `/run/secrets`.
- **Read as properties.** A Spring service imports `/run/secrets` as a config tree (`spring.config.import: optional:configtree:…`), and the secret's target there is the property it sets, such as `spring.datasource.password`. So a secret is an ordinary property: a test sets it as one, and no code knows where it came from. An image whose software reads secrets from files (Postgres, Grafana, pgAdmin) is given the file's path; one that only reads its environment (Keycloak, Temporal, Apicurio) has it read from the file into its environment as it starts, so the value never appears in the container's configuration.
- **Keycloak's clients agree with their services.** The realm file holds no secret. Realm sync sets each confidential client's secret from the same file its service reads, on every start, and changes nothing else that already exists.
- **Existing stacks keep their databases.** Postgres, Couchbase and Keycloak's bootstrap admin keep the password they were initialised with, so on a stack whose volumes already exist `make secrets` writes the infrastructure passwords with the old dev values. Client and webhook secrets are always new, which realm sync makes safe.
- **CI** scans the repo and its history with Gitleaks and fails on a finding. Besides Gitleaks' own rules, [`.gitleaks.toml`](../../.gitleaks.toml) catches any literal password or secret in a configuration file. The dev values committed before this change are allow-listed one by one, by fingerprint, in `.gitleaksignore`, not by a pattern that a new secret could also match. CI also generates a stack's secrets and checks that `docker compose config` holds none of them.

This is the shape a secrets manager takes on in Sprint 8: in AWS, a secret is mounted as a file at the same path (by the Secrets Store CSI driver, or an ECS task's secrets written to a volume), and the services read it the same way.

## Considered Options

- **Environment variables from a git-ignored `.env`.** Rejected because Compose's resolved configuration, `docker inspect` and every process in the container see them, and every container would get every secret.
- **A secrets manager now** (Vault, or LocalStack's Secrets Manager). Rejected as a running service and a client in every service for what files already give; it comes with Sprint 8's move to AWS, behind the same files.
- **Keep dev constants and only scan for real ones.** Rejected because one leaked dev file would then open every stack, and nothing stops a constant reaching a deployed environment.

## Consequences

- `.secrets/` is git-ignored and excluded from every image's build context. Only the owner can open the directory. The files in it are readable by others, since Compose bind-mounts each file as it is and the container's user is not the file's owner on Linux; the directory's permissions are what keep other local users out.
- The demo Customer's and the Staff user's passwords and the public `dev-cli` client stay in the realm file as demo data for trying the stack and for the tests. They are not secrets.
- Deleting an infrastructure password's file while the stack's volumes exist would give it the old dev value, which a stack made since doesn't have; the README warns against it.
- Every Postgres service still shares the `ecomm` login, and so `postgres-password`, until Sprint 9 gives each its own.
- Rotation is by hand. Automated rotation is out of scope until the secrets manager.
- Running a service outside Docker, against the compose stack, takes its secrets from `.secrets/` as environment variables, as each service's README shows.
