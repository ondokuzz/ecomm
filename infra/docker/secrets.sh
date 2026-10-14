#!/bin/sh
# Writes each of the stack's secrets that is missing to .secrets/<Compose project>/<name>, from
# which Compose mounts each container the ones it needs (docs/adr/0011-secrets-as-generated-files.md).
# A secret that is already there is never overwritten. Run from the repo root as `make secrets`,
# which `make up` runs first.
#
# A new secret is random and URL-safe. The infrastructure passwords are the exception on a stack
# whose data volumes already exist: Postgres, Couchbase and Keycloak keep the password they were
# initialised with, so these get the dev values the stack was made with before secrets were files.
# The README says how to rotate one by hand.
set -eu

project=${COMPOSE_PROJECT_NAME:-ecomm}
dir=.secrets/$project

# Only the owner can open the directory, and so reach any secret in it. On macOS the files are
# owner-only too: Docker Desktop lets a container's user read a bind-mounted file whatever its mode.
# On Linux they are readable by all, since Compose bind-mounts each one as it is, and a container's
# user is not the file's owner there.
mkdir -p "$dir"
chmod 700 .secrets "$dir"
if [ "$(uname)" = Darwin ]; then
  umask_files=077
else
  umask_files=022
fi

# Whether the project has this volume. Docker has to answer: a stack's volumes can't be told
# apart from no volumes while it is down.
volume_exists() {
  if ! docker info >/dev/null 2>&1; then
    echo "Docker isn't running, so whether $project's volumes exist is unknown" >&2
    exit 1
  fi
  docker volume inspect "${project}_$1" >/dev/null 2>&1
}

random() {
  head -c 32 /dev/urandom | base64 | tr '+/' '-_' | tr -d '=\n'
}

# write <name> <value>
write() {
  if [ -e "$dir/$1" ]; then
    return
  fi
  (umask "$umask_files" && printf '%s' "$2" >"$dir/$1.new")
  mv "$dir/$1.new" "$dir/$1"
  echo "Wrote $dir/$1"
}

# infrastructure <name> <volume> <the dev value a stack made before Sprint 5 has>
infrastructure() {
  if [ -e "$dir/$1" ]; then
    return
  elif volume_exists "$2"; then
    write "$1" "$3"
  else
    write "$1" "$(random)"
  fi
}

# Postgres's ecomm login, which every Postgres service shares (Keycloak's database among them)
# until Sprint 9 gives each its own.
infrastructure postgres-password postgres-data ecomm
infrastructure couchbase-password couchbase-data password
# Keycloak's bootstrap admin lives in its database, on Postgres's volume.
infrastructure keycloak-admin-password postgres-data admin
# Grafana and pgAdmin keep no data, but keep their dev password on an existing stack too, so no
# sign-in changes in the upgrade.
infrastructure grafana-admin-password postgres-data admin
infrastructure pgadmin-password postgres-data admin

# The confidential Keycloak clients' secrets and the webhook secrets are always new: realm sync
# sets each client's secret from its file on every start.
write checkout-client-secret "$(random)"
write orchestration-client-secret "$(random)"
write payment-webhook-secret "$(random)"
write carrier-webhook-secret "$(random)"

# On macOS, a file written before secrets were owner-only there is made so, its content unchanged.
if [ "$umask_files" = 077 ]; then
  for file in "$dir"/*; do
    if [ -f "$file" ]; then chmod 600 "$file"; fi
  done
fi
