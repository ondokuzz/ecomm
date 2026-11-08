#!/usr/bin/env bash
# Runs both Playwright suites against a stack of their own, the ecomm-e2e Compose project, so a run
# starts on the seed, as in CI, and leaves the default stack's data as it was (#121). Run from the
# repo root as `make e2e`.
#
# The two stacks share host ports and don't fit in 8 GB together, so the default stack is stopped
# first, keeping its data, and isn't started again: `make up` brings it back. The e2e stack starts
# on fresh volumes and secrets each run, and they are removed after: on 2026-11-08 that took 7.7
# minutes to a warm stack, against 11.6 for `make seed-reset` on the volumes a run left. Its images
# are the default stack's, so a tree they were built from builds nothing.
#
# A failed run saves the e2e stack's logs to .scratch/e2e/compose.log before removing it.
set -euo pipefail

export COMPOSE_PROJECT_NAME=ecomm-e2e
# The suites aim at the stack on the default ports, run `make status` against it, and expect the
# Support Assistant's mock provider, which an empty key, as the e2e stack's is written, runs.
unset STOREFRONT_URL ADMIN_CONSOLE_URL KEYCLOAK_URL ANTHROPIC_API_KEY_FILE
compose_all=(docker compose --profile '*')
logs=.scratch/e2e/compose.log
began=$SECONDS

remove() {
  "${compose_all[@]}" down -v --remove-orphans
  rm -rf ".secrets/$COMPOSE_PROJECT_NAME"
}

finish() {
  local status=$?
  trap - EXIT INT TERM
  if [ "$status" -ne 0 ]; then
    mkdir -p "$(dirname "$logs")"
    docker compose logs --no-color --timestamps >"$logs" 2>&1 || true
    echo "The e2e stack's logs are in $logs"
  fi
  remove
  echo "make e2e took $(( (SECONDS - began) / 60 )) min $(( (SECONDS - began) % 60 )) s." \
    "The default stack is down, its data kept: make up brings it back."
  exit "$status"
}

COMPOSE_PROJECT_NAME=ecomm "${compose_all[@]}" down
# An interrupted run's stack, so this one starts on fresh volumes.
remove
trap finish EXIT
trap 'exit 130' INT TERM

infra/docker/secrets.sh
docker compose up -d --build --wait || { infra/docker/stack-status.sh; exit 1; }
# Docker can present the host's requests with a public source IP, and a new master realm requires
# HTTPS for those, refusing the suites' and the warm-up's admin tokens with "HTTPS required". The
# ecomm realm's file already sets none (services/identity-access/README.md).
docker compose exec -T keycloak sh -c '
  /opt/keycloak/bin/kcadm.sh config credentials --server http://localhost:8080 --realm master \
    --user admin --password "$(cat /run/secrets/keycloak-admin-password)" &&
  /opt/keycloak/bin/kcadm.sh update realms/master -s sslRequired=NONE'
python3 -B infra/docker/warm-up.py

# The Admin Console's suite runs after the Storefront's rather than beside it, since both check out,
# and even when the Storefront's fails, so one run says what each would.
failed=0
(cd frontend/storefront && npx playwright test --workers=1) || failed=1
(cd frontend/admin-console && npx playwright test --workers=1) || failed=1
exit "$failed"
