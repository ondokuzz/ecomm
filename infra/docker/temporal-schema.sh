#!/bin/sh
# Sets up Temporal's schema in its two databases on the shared Postgres, or migrates it to the
# server's version, then exits. Both steps are no-ops on a schema that is already current, so this
# runs on every `docker compose up`, before the server starts.
set -eu

for db in temporal:temporal temporal_visibility:visibility; do
  name=${db%%:*}
  schema=/etc/temporal/schema/postgresql/v12/${db##*:}/versioned
  temporal-sql-tool --db "$name" setup-schema -v 0.0
  temporal-sql-tool --db "$name" update-schema -d "$schema"
done
