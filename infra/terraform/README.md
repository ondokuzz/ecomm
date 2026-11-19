# The AWS environment

An AWS environment is one Environment of the platform (the glossary's term), built in an account
for a test run and destroyed after it: `make aws-up` builds it, or brings the one there is up to the
tree and SHA it is given, and `make aws-down` destroys it. Why it is shaped this way:
[ADR 0012](../../docs/adr/0012-deployed-with-kustomize-to-eks-with-managed-postgres-and-redis-only.md).

```
infra/terraform/
  bootstrap/      the state bucket, applied once per account and region; its own state is local
  environment/    everything else, with its state in that bucket
    network.tf    a VPC over two zones, one NAT gateway
    cluster.tf    EKS, its node group, its add-ons (the EBS CSI driver among them)
    stores.tf     RDS Postgres, ElastiCache Redis, the RDS password in Secrets Manager
    ingress.tf    the ALB, its certificate and Route 53 records, the AWS Load Balancer Controller
    budget.tf     the budget alarm
  .tflint.hcl     tflint's rules, for both roots
infra/aws/
  aws-up.sh       make aws-up
  aws-down.sh     make aws-down
  run-overlay.py  the run's overlay over infra/k8s/overlays/aws
```

## What it builds

| | |
|---|---|
| Network | A VPC, `10.0.0.0/16`, across the region's first two zones: private subnets for the nodes, RDS and ElastiCache, public ones for the ALB and one NAT gateway |
| Cluster | EKS on Kubernetes 1.35, one managed node group of four `m6i.large` nodes (amd64, as CI builds the images), its API public for the operator's `kubectl`. Add-ons: CoreDNS, kube-proxy, the VPC CNI, the Pod Identity agent and the EBS CSI driver, whose volumes back the StatefulSets as gp3 |
| Postgres | RDS Postgres 16, one `db.t4g.medium` in one zone, a day of backups, holding every database the local Postgres does |
| Redis | ElastiCache Redis 7, one `cache.t4g.small` node, with expired-key events on as locally |
| Ingress | An ALB with an ACM certificate for `shop.`, `admin.`, `api.` and `auth.` under the domain, each a Route 53 alias to it, sending to the Storefront, the Admin Console, the API gateway and Keycloak. HTTP redirects to HTTPS |
| Budget | An email at 80% of the account's monthly cost limit, $25 unless `budget_limit` is set, and when the month's forecast passes it |

Everything else runs in the cluster, as the [`aws` overlay](../k8s/overlays/aws/kustomization.yaml)
over the [base](../k8s/README.md) has it: Kafka, Mongo, Couchbase, Keycloak, Temporal, Apicurio,
Mailpit, Prometheus and Grafana, and every service.

Every resource is tagged `project=ecomm` and `run=<id>`; the region is `eu-central-1` unless
`REGION` is given to both make targets. The platform requests about 17.5 GiB and 4.5 vCPUs, which four
nodes hold with room for a rollout; `node_count` sets another number.

**It costs about $0.77 an hour, so $18 a day**, in `eu-central-1`: the four nodes are $0.46 of it,
EKS's control plane $0.10, RDS $0.07, the NAT gateway $0.05, ElastiCache $0.04 and the ALB $0.03.
The budget alarm is over the whole account, since a tag counts in a budget only a day after it is
activated as a cost allocation tag; it also catches what a run left behind.

## Prerequisites

`make aws-up` checks each before it makes anything:

- An AWS account, and credentials for it that the AWS CLI finds (`aws sts get-caller-identity`
  answers), allowed to make all of the above: an administrator's, for a test account.
- A **public Route 53 hosted zone** for the domain, which is `DOMAIN`. The environment's names are
  made in it, under the domain itself: `DOMAIN=ecomm.example.com` gives
  `https://shop.ecomm.example.com`.
- **A GitHub token** with `read:packages`, as `GHCR_TOKEN`, and its owner's login as `GHCR_USER`,
  in the environment, not on the command line. The repository is private, so its images are too.
- **A commit on `main`** as `SHA`, all 40 characters: CI's `images` job pushes
  `ghcr.io/ondokuzz/ecomm/<service>:<sha>` for each one. It must be this change or later, as the
  frontends read their Keycloak from the page since.
- `EMAIL`, the address the budget alarm emails. AWS asks nothing of it to confirm.
- The AWS CLI v2, Terraform 1.11 or later (for its write-only arguments), `kubectl`, `curl` and
  Python 3. Docker isn't needed.

## `make aws-up`

```sh
export GHCR_USER=<your GitHub login> GHCR_TOKEN=<a token with read:packages>
make aws-up DOMAIN=ecomm.example.com SHA=$(git rev-parse ecomm/main) EMAIL=you@example.com
```

1. **Checks** the prerequisites: the tools, the credentials, the hosted zone, and every image the
   overlay names, at `SHA`, through the token.
2. **Makes the state bucket**, `ecomm-tfstate-<account>-<region>`, if there is none
   ([`bootstrap`](./bootstrap/main.tf)): versioned, encrypted, never public. The environment's
   state is in it as `environment.tfstate`, locked while an apply runs by S3's own lock file. The
   bootstrap's state is local and git-ignored: the bucket is found again by its name.
3. **Applies [`environment`](./environment)** with the run's ID: a new environment's is the time it
   is made (`202611191430`), and one that exists keeps its own, from its state. Making one takes
   about 25 minutes, mostly EKS and RDS.
4. **Writes the secrets.** RDS's master password is generated by Terraform and given to RDS and to
   Secrets Manager as write-only arguments, so it is in no state, and copied from Secrets Manager to
   `.secrets/ecomm-aws/postgres-password`. Then `make secrets` writes the rest, for the `ecomm-aws`
   project, as for a local stack ([ADR 0011](../../docs/adr/0011-secrets-as-generated-files.md)).
   Each file becomes a Secret of its name in the `ecomm` namespace, and the pods mount them as files
   where Compose does. The pull secret `ghcr` is made from the token, through stdin.
5. **Applies the platform**: [`run-overlay.py`](../aws/run-overlay.py) writes a run's overlay over
   `infra/k8s/overlays/aws` into `.scratch/aws/overlay`, with the run's values from Terraform's
   outputs in place of the overlay's examples ([`environment.env`](../k8s/overlays/aws/environment.env)),
   the images at `SHA`, and the realm with the two frontends' clients redirecting to their
   subdomains only and `sslRequired` external. The finished Jobs are deleted first, so they run
   again, as Compose's one-shot steps do on every `up`.
6. **Waits** for every Deployment's and StatefulSet's rollout and every Job, then for each URL to
   answer, and for Keycloak's issuer to be `https://auth.<domain>/realms/ecomm`, and prints the URLs.
   The first start takes some restarts, as the init Jobs and the services that need them start
   together ([infra/k8s/README.md](../k8s/README.md#what-the-base-runs)).

The run's files are in `.scratch/aws/`: the kubeconfig, Terraform's outputs and the overlay.

### How it is reached

The ALB is Terraform's, with a target group per name, and the AWS Load Balancer Controller only
registers each Service's ready pods in its group, through a `TargetGroupBinding` in the overlay. So
the Route 53 records are made in the same apply as the ALB they name, and nothing the controller
makes outlives the cluster. Keycloak is behind the ALB's TLS: it takes the browser's scheme from
`X-Forwarded-Proto`, so the realm's `sslRequired` external holds for the browser, while the
services, on the cluster's private addresses, call it over plain HTTP. Tokens carry
`https://auth.<domain>/realms/ecomm` as their issuer, which every service is set to trust.

RDS refuses plain connections by default from Postgres 15 on; the environment's parameter group
allows them inside the VPC, since Temporal's SQL plugin doesn't ask for TLS unless configured to.

### Reaching what isn't public

```sh
export KUBECONFIG=$PWD/.scratch/aws/kubeconfig
kubectl -n ecomm get pods
kubectl -n ecomm port-forward svc/grafana 3000:3000        # http://localhost:3000, admin and .secrets/ecomm-aws/grafana-admin-password
kubectl -n ecomm port-forward svc/mailpit 8025:8025        # the environment's mail
kubectl -n ecomm port-forward svc/temporal-ui 8233:8080    # the Sagas
```

Postgres is reached through a relay pod, as [infra/k8s/README.md](../k8s/README.md) shows, with
`.secrets/ecomm-aws/postgres-password`.

### The smoke tests against it

Both Playwright suites take their URLs from the environment. They reach Keycloak at `auth.`, and
some services and Mailpit directly, so those are port-forwarded first, each in its own terminal:

```sh
export KUBECONFIG=$PWD/.scratch/aws/kubeconfig
kubectl -n ecomm port-forward svc/order-management 8085:8080
kubectl -n ecomm port-forward svc/payment 8084:8080
kubectl -n ecomm port-forward svc/fulfillment-shipping 8099:8080
kubectl -n ecomm port-forward svc/mailpit 8025:8025

export COMPOSE_PROJECT_NAME=ecomm-aws KEYCLOAK_URL=https://auth.ecomm.example.com
(cd frontend/storefront && STOREFRONT_URL=https://shop.ecomm.example.com npm run test:e2e -- --workers=1)
(cd frontend/admin-console && ADMIN_CONSOLE_URL=https://admin.ecomm.example.com \
  STOREFRONT_URL=https://shop.ecomm.example.com npm run test:e2e -- --workers=1)
```

`COMPOSE_PROJECT_NAME` points them at the environment's secrets. Set, the URLs skip the suites'
checks of the local stack. The cart-reminder test needs Cart's abandonment shortened, as CI's
`e2e` job does, which the environment doesn't; it is skipped without `CART_ABANDONED_AFTER`.

## `make aws-down`

```sh
make aws-down
```

1. **Deletes the `ecomm` namespace** and waits for every volume to go: the EBS volumes are the CSI
   driver's to delete, and the target groups' pods the controller's to deregister, which they do
   only while the cluster runs.
2. **Destroys** everything in the environment's state.
3. **Checks by the run's tag** that nothing is left, in the region and in `us-east-1` for IAM,
   asking again for up to 10 minutes as AWS's tagging API lists a deleted resource for a while. It
   fails naming whatever is still there; `make aws-down` again picks up where it stopped.

It leaves the state bucket, tagged `run=bootstrap`, which serves every run and costs next to
nothing. To remove it too, empty it of every version and delete it, or
`terraform -chdir=infra/terraform/bootstrap destroy` with its local state.

## Checks

CI's `k8s` job runs `terraform fmt -check`, `terraform validate` without a backend and `tflint` on
both roots, and builds and validates the `aws` overlay with the others. Nothing in CI applies. To
run them here, with Docker:

```sh
docker run --rm -v "$PWD/infra/terraform:/tf" -w /tf/environment hashicorp/terraform:1.17.1 init -backend=false
docker run --rm -v "$PWD/infra/terraform:/tf" -w /tf/environment hashicorp/terraform:1.17.1 validate
docker run --rm -v "$PWD/infra/terraform:/tf" -w /tf --entrypoint sh ghcr.io/terraform-linters/tflint:v0.65.0 \
  -c 'tflint --init --config /tf/.tflint.hcl && tflint --chdir environment --config /tf/.tflint.hcl'
```
