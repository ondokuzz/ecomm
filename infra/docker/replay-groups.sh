#!/bin/sh
# Sets each consumer group named to read its topics again from the start, except its redrive topic,
# which it reads from the end: what was redriven is still on the topic it came from. Run from the
# repo root by the Makefile's rebuild targets, once the service whose groups they are is stopped.
#
# A stopped service's consumers may not have left their groups: one killed while closing leaves its
# group Stable until the broker's session timeout, and Kafka refuses to reset a group that isn't
# empty. kafka-consumer-groups.sh says so but exits 0, so this waits up to a minute for each group
# to be empty, and fails if it isn't or a reset says Error, before anything is emptied.
set -eu

groups() { docker compose exec -T kafka /opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server localhost:19092 "$@"; }

for group in "$@"; do
  waited=0
  until groups --describe --group "$group" --state | grep -qE '[[:space:]](Empty|Dead)[[:space:]]'; do
    if [ "$waited" -ge 60 ]; then
      echo "Consumer group $group still has members after $waited seconds; is its service stopped?" >&2
      exit 1
    fi
    waited=$((waited + 2))
    sleep 2
  done
  for reset in "--to-earliest --all-topics" "--to-latest --topic $group.redrive"; do
    # shellcheck disable=SC2086
    out=$(groups --group "$group" --reset-offsets $reset --execute 2>&1)
    echo "$out"
    if echo "$out" | grep -q '^Error'; then exit 1; fi
  done
done
