#!/usr/bin/env bash
# Destroys the AWS environment `make aws-up` built: `make aws-down`, with the same REGION if it
# was given one. Run from the repo root. See infra/terraform/README.md.
#
# 1. Deletes the namespace first, and waits for its volumes to go: the EBS volumes are the CSI
#    driver's, which it deletes only while the cluster still runs, and the target groups' pods
#    are deregistered by the Load Balancer Controller likewise.
# 2. Destroys every resource in Terraform's state.
# 3. Checks, by the run's tag, that nothing is left, and fails naming what is.
#
# The state bucket is left, as it serves every run (infra/terraform/bootstrap).
set -euo pipefail

REGION=${REGION:-eu-central-1}
NAMESPACE=ecomm
RUN_DIR=.scratch/aws
export KUBECONFIG=$PWD/$RUN_DIR/kubeconfig

fail() {
  echo "aws-down: $*" >&2
  exit 1
}

for tool in aws terraform kubectl; do
  command -v "$tool" >/dev/null || fail "$tool isn't installed (infra/terraform/README.md lists the prerequisites)"
done
account=$(aws sts get-caller-identity --query Account --output text 2>/dev/null) ||
  fail "No AWS credentials work: aws sts get-caller-identity failed"
bucket=ecomm-tfstate-$account-$REGION
aws s3api head-bucket --bucket "$bucket" 2>/dev/null ||
  fail "Account $account has no state bucket in $REGION, so no AWS environment of make aws-up's"

tf() { terraform -chdir=infra/terraform/environment "$@"; }
tf init -input=false -reconfigure -backend-config="bucket=$bucket" -backend-config="region=$REGION" >/dev/null
# The run and domain come from the state's outputs. A first apply that failed part-way may have left
# resources without them, so then they are given: RUN as make aws-up printed it, and its DOMAIN.
run=${RUN:-$(tf output -raw run 2>/dev/null || true)}
domain=${DOMAIN:-$(tf output -raw domain 2>/dev/null || true)}
if [ -z "$run" ] || [ -z "$domain" ]; then
  if [ -z "$(tf state list)" ]; then
    echo "The state has no AWS environment in $REGION; nothing to destroy"
    exit 0
  fi
  fail "The state holds resources but no run ID or domain: give the ones make aws-up printed, e.g. make aws-down RUN=202611191430 DOMAIN=ecomm.example.com"
fi
echo "Destroying run $run ($domain) in $REGION"

# --- 1. The namespace ----------------------------------------------------------------------------

# A cluster an earlier, failed aws-down already destroyed leaves no namespace to delete.
if cluster=$(tf output -raw cluster_name 2>/dev/null) &&
  aws eks describe-cluster --region "$REGION" --name "$cluster" >/dev/null 2>&1; then
  aws eks update-kubeconfig --region "$REGION" --name "$cluster" --kubeconfig "$KUBECONFIG" >/dev/null
  kubectl delete namespace "$NAMESPACE" --ignore-not-found --timeout=20m
  echo "Waiting for the volumes to be deleted"
  for _ in $(seq 60); do
    [ -z "$(kubectl get persistentvolumes -o name)" ] && break
    sleep 10
  done
  [ -z "$(kubectl get persistentvolumes -o name)" ] ||
    fail "Volumes are left after 10 minutes: $(kubectl get persistentvolumes -o name | tr '\n' ' ')"
fi

# --- 2. Terraform's resources --------------------------------------------------------------------

# Destroying reads no variable but the run's and the domain's; the budget's address is given only
# because the variable has no default.
tf destroy -input=false -auto-approve \
  -var "run=$run" -var "region=$REGION" -var "domain=$domain" -var "budget_email=unused@example.com"

# --- 3. Nothing left -----------------------------------------------------------------------------

# The tagging API goes on listing a resource for some minutes after it is deleted, so it is asked
# again until it lists none, for up to 10 minutes. us-east-1 too, for the global resources (IAM).
echo "Checking that nothing tagged run=$run is left"
left() {
  for region in "$REGION" us-east-1; do
    aws resourcegroupstaggingapi get-resources --region "$region" \
      --tag-filters "Key=project,Values=ecomm" "Key=run,Values=$run" \
      --query 'ResourceTagMappingList[].ResourceARN' --output text
  done | tr '\t' '\n' | sed '/^$/d' | sort -u
}
for _ in $(seq 20); do
  remaining=$(left)
  [ -z "$remaining" ] && break
  sleep 30
done
if [ -n "$remaining" ]; then
  echo "These are still tagged run=$run:" >&2
  echo "$remaining" >&2
  fail "Delete them, or run make aws-down again"
fi

rm -f "$KUBECONFIG" "$RUN_DIR/outputs.json"
echo "Run $run is destroyed; nothing tagged with it is left."
