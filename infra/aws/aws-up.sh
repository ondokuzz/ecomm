#!/usr/bin/env bash
# Builds the AWS environment, or brings an existing one up to this tree and SHA: `make aws-up
# DOMAIN=<hosted zone> SHA=<commit> EMAIL=<budget alarm's address>`, with GHCR_USER and GHCR_TOKEN
# in the environment. Run from the repo root. See infra/terraform/README.md.
#
# 1. Checks every prerequisite before anything is made.
# 2. Makes Terraform's state bucket if the account and region have none (infra/terraform/bootstrap).
# 3. Applies infra/terraform/environment.
# 4. Writes the Environment's secrets (`make secrets`, for the ecomm-aws project) with RDS's password,
#    and creates one Secret per file, which the pods mount as files at the paths Compose does.
# 5. Applies the run's overlay over infra/k8s/overlays/aws, waits for every rollout and init Job,
#    and for each URL to answer, then prints them.
set -euo pipefail

REGION=${REGION:-eu-central-1}
PROJECT=ecomm-aws
NAMESPACE=ecomm
RUN_DIR=.scratch/aws
# The run's own kubeconfig, so the operator's current context is left alone.
export KUBECONFIG=$PWD/$RUN_DIR/kubeconfig

fail() {
  echo "aws-up: $*" >&2
  exit 1
}

# --- 1. Prerequisites ----------------------------------------------------------------------------

[ -n "${DOMAIN:-}" ] || fail "Set DOMAIN to a Route 53 hosted zone's domain, e.g. make aws-up DOMAIN=ecomm.example.com SHA=… EMAIL=…"
[ -n "${SHA:-}" ] || fail "Set SHA to a commit on main whose images CI pushed, e.g. SHA=\$(git rev-parse ecomm/main)"
[[ $SHA =~ ^[0-9a-f]{40}$ ]] || fail "SHA must be a full 40-character commit SHA, as CI tags the images with"
[ -n "${EMAIL:-}" ] || fail "Set EMAIL to the address the budget alarm emails"
[ -n "${GHCR_USER:-}" ] && [ -n "${GHCR_TOKEN:-}" ] ||
  fail "Set GHCR_USER and GHCR_TOKEN (a token with read:packages) in the environment, to pull the images"

for tool in aws terraform kubectl python3 curl; do
  command -v "$tool" >/dev/null || fail "$tool isn't installed (infra/terraform/README.md lists the prerequisites)"
done
python3 -c '
import json, sys
v = tuple(int(x) for x in json.load(sys.stdin)["terraform_version"].split(".")[:2])
sys.exit(v < (1, 11))' < <(terraform version -json) || fail "Terraform 1.11 or later is needed"

account=$(aws sts get-caller-identity --query Account --output text 2>/dev/null) ||
  fail "No AWS credentials work: aws sts get-caller-identity failed"
echo "AWS account $account, region $REGION"

# shellcheck disable=SC2016 # the backticks are JMESPath's literals
zone=$(aws route53 list-hosted-zones-by-name --dns-name "$DOMAIN." \
  --query 'HostedZones[?Name==`'"$DOMAIN."'` && Config.PrivateZone==`false`] | [0].Id' --output text)
[ -n "$zone" ] && [ "$zone" != None ] || fail "The account has no public Route 53 hosted zone for $DOMAIN"
echo "Hosted zone $zone for $DOMAIN"

# Every image the overlay names, at SHA, through the token: proves the token, and that CI pushed them.
images=$(sed -n 's/.*newName: ghcr\.io\/\([^,]*\),.*/\1/p' infra/k8s/overlays/aws/kustomization.yaml)
for image in $images; do
  token=$(curl -fsS -u "$GHCR_USER:$GHCR_TOKEN" "https://ghcr.io/token?scope=repository:$image:pull" |
    python3 -c 'import json, sys; print(json.load(sys.stdin)["token"])') ||
    fail "GHCR refused GHCR_USER and GHCR_TOKEN for $image"
  curl -fsS -o /dev/null -I -H "Authorization: Bearer $token" \
    -H 'Accept: application/vnd.oci.image.index.v1+json, application/vnd.oci.image.manifest.v1+json, application/vnd.docker.distribution.manifest.v2+json' \
    "https://ghcr.io/v2/$image/manifests/$SHA" ||
    fail "ghcr.io/$image:$SHA doesn't exist: CI pushes images for commits on main only"
done
echo "Every image is in GHCR at $SHA"

mkdir -p "$RUN_DIR"

# --- 2. Terraform's state bucket -----------------------------------------------------------------

bucket=ecomm-tfstate-$account-$REGION
if ! aws s3api head-bucket --bucket "$bucket" 2>/dev/null; then
  echo "Making the state bucket $bucket"
  terraform -chdir=infra/terraform/bootstrap init -input=false
  terraform -chdir=infra/terraform/bootstrap apply -input=false -auto-approve -var "region=$REGION"
fi

# --- 3. The environment's resources --------------------------------------------------------------

tf() { terraform -chdir=infra/terraform/environment "$@"; }
tf init -input=false -reconfigure -backend-config="bucket=$bucket" -backend-config="region=$REGION"
# An AWS environment that exists keeps its run ID; a new one is named after when it is made.
run=$(tf output -raw run 2>/dev/null) || run=$(date -u +%Y%m%d%H%M)
echo "Run $run"
tf apply -input=false -auto-approve \
  -var "run=$run" -var "region=$REGION" -var "domain=$DOMAIN" -var "budget_email=$EMAIL"
tf output -json >"$RUN_DIR/outputs.json"
output() { python3 -c 'import json, sys; print(json.load(open(sys.argv[1]))[sys.argv[2]]["value"])' "$RUN_DIR/outputs.json" "$1"; }

aws eks update-kubeconfig --region "$REGION" --name "$(output cluster_name)" --kubeconfig "$KUBECONFIG"

# --- 4. Secrets ----------------------------------------------------------------------------------

# RDS's password is the run's Postgres password, so it is written over any an earlier run left;
# `make secrets` then writes the rest that are missing, as for a local stack.
secrets=.secrets/$PROJECT
mkdir -p "$secrets"
chmod 700 .secrets "$secrets"
(umask 077 && aws secretsmanager get-secret-value --region "$REGION" \
  --secret-id "$(output postgres_password_secret)" --query SecretString --output text |
  tr -d '\n' >"$secrets/postgres-password")
COMPOSE_PROJECT_NAME=$PROJECT SECRETS_WITHOUT_VOLUMES=1 infra/docker/secrets.sh

kubectl apply -f infra/k8s/base/namespace.yaml
for file in "$secrets"/*; do
  name=$(basename "$file")
  kubectl -n "$NAMESPACE" create secret generic "$name" --from-file="$name=$file" \
    --dry-run=client -o yaml | kubectl apply -f -
done
# The pull secret, from the environment through stdin, so the token is in no command line.
python3 -c '
import base64, json, os
credentials = os.environ["GHCR_USER"] + ":" + os.environ["GHCR_TOKEN"]
auth = base64.b64encode(credentials.encode()).decode()
print(json.dumps({"auths": {"ghcr.io": {"auth": auth}}}))' |
  kubectl -n "$NAMESPACE" create secret generic ghcr --type=kubernetes.io/dockerconfigjson \
    --from-file=.dockerconfigjson=/dev/stdin --dry-run=client -o yaml | kubectl apply -f -

# --- 5. The platform ------------------------------------------------------------------------------

python3 -B infra/aws/run-overlay.py "$RUN_DIR/outputs.json" "$SHA" "$RUN_DIR/overlay"
# A Job's pod template can't be changed, so the finished ones go, and run again, as Compose's
# one-shot steps do on every `up`.
kubectl -n "$NAMESPACE" delete job --all --ignore-not-found
# Server-side, since the realm's and the theme's ConfigMaps are larger than the annotation a
# client-side apply keeps the last configuration in.
kubectl kustomize --load-restrictor LoadRestrictionsNone "$RUN_DIR/overlay" |
  kubectl apply --server-side --force-conflicts -f -

# The init Jobs and the services that need them come up together, each retried until what it needs
# is there (infra/k8s/README.md), so the first start takes some restarts.
echo "Waiting for every rollout"
for workload in $(kubectl -n "$NAMESPACE" get deployments,statefulsets -o name); do
  kubectl -n "$NAMESPACE" rollout status "$workload" --timeout=20m
done
kubectl -n "$NAMESPACE" wait --for=condition=complete job --all --timeout=20m

# The ALB sends to a pod once its health check passes, and the names resolve once Route 53's
# records have spread. Each is asked for a page it answers without signing in, and Keycloak for its
# realm's configuration, whose issuer must be the one every service trusts.
echo "Waiting for each URL to answer"
answers() {
  for _ in $(seq 60); do
    if curl -fsS -o /dev/null "$1"; then return; fi
    sleep 10
  done
  fail "$1 didn't answer within 10 minutes"
}
answers "https://shop.$DOMAIN/"
answers "https://admin.$DOMAIN/"
answers "https://api.$DOMAIN/actuator/health/readiness"
answers "https://auth.$DOMAIN/realms/ecomm/.well-known/openid-configuration"
issuer=$(curl -fsS "https://auth.$DOMAIN/realms/ecomm/.well-known/openid-configuration" |
  python3 -c 'import json, sys; print(json.load(sys.stdin)["issuer"])')
[ "$issuer" = "https://auth.$DOMAIN/realms/ecomm" ] ||
  fail "Keycloak issues tokens for $issuer, not https://auth.$DOMAIN/realms/ecomm"

echo
echo "The environment (run $run) is up:"
python3 -c '
import json, sys
for service, url in json.load(open(sys.argv[1]))["urls"]["value"].items():
    print(f"  {service:15} {url}")' "$RUN_DIR/outputs.json"
echo "kubectl reaches it with KUBECONFIG=$KUBECONFIG. make aws-down destroys it."
