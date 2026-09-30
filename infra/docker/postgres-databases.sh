#!/bin/sh
# One database per Postgres-backed service. Creates each one that is missing, so a stack whose
# volume was initialized before a service was added gets that service's database too; the ones that
# exist are left alone. Runs once per `docker compose up`, before anything that uses them.
set -eu

for db in keycloak inventory payment orders promotions; do
  if ! psql -tAc "SELECT 1 FROM pg_database WHERE datname = '$db'" | grep -q 1; then
    psql -v ON_ERROR_STOP=1 -c "CREATE DATABASE $db"
    echo "Created database $db"
  fi
done
