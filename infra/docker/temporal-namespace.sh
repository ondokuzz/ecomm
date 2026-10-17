#!/bin/sh
# Creates the namespace every workflow runs in, `default`, keeping a closed workflow's history for 7
# days, or sets that retention on the namespace if it exists. Then exits; runs on every
# `docker compose up`, once the server is up.
#
# A describe can fail just after the server reports healthy, while it is still loading namespaces,
# though the namespace exists. So a create that finds it exists sets the retention instead, and any
# other failure is tried again, for up to a minute.
set -u

# The server may exit while starting and be restarted (its `restart: on-failure`), so wait for it
# for about 5 minutes rather than for ever, which left `make up` waiting with nothing to say.
waited=0
until temporal operator cluster health >/dev/null 2>&1; do
  if [ "$waited" -ge 300 ]; then
    echo "Temporal's server wasn't healthy after $waited seconds" >&2
    exit 1
  fi
  waited=$((waited + 1))
  sleep 1
done

retention() { temporal operator namespace update --namespace default --retention 168h; }

attempt=1
while :; do
  if temporal operator namespace describe --namespace default >/dev/null 2>&1; then
    retention && exit 0
  elif out=$(temporal operator namespace create --namespace default --retention 168h 2>&1); then
    echo "$out"
    exit 0
  elif echo "$out" | grep -qi 'already exists'; then
    retention && exit 0
  else
    echo "$out" >&2
  fi
  if [ "$attempt" -ge 30 ]; then
    echo "Could not create or update the default namespace after $attempt attempts" >&2
    exit 1
  fi
  attempt=$((attempt + 1))
  sleep 2
done
