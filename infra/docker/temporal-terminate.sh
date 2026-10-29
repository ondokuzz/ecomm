#!/bin/sh
# Terminates every running workflow in the namespace `default`, then waits until Temporal lists none
# running. `make seed-reset` runs it, in the temporal-namespace service's admin-tools image, since
# it drops the data the Sagas act on: terminated rather than cancelled, so no compensation runs
# against Orders, Payments, Shipments or RMAs that are gone.
#
# Termination by query runs as a batch in the background, and the visibility store lags behind it,
# so the count is read until it reaches 0, for up to 2 minutes.
set -eu

query="ExecutionStatus='Running'"
running() { temporal workflow count --query "$query" | sed -n 's/^Total: //p'; }

n=$(running)
if [ "$n" -gt 0 ]; then
  temporal workflow terminate --query "$query" --reason "make seed-reset dropped its data" --yes
fi

waited=0
while [ "$n" -gt 0 ]; do
  if [ "$waited" -ge 120 ]; then
    echo "$n workflows were still running after $waited seconds" >&2
    exit 1
  fi
  waited=$((waited + 1))
  sleep 1
  n=$(running)
done
echo "No workflow is running"
