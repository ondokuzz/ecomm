#!/bin/sh
# Creates the event backbone's topics, then exits. The broker creates no topics itself. An existing
# topic is left alone. Every topic has 6 partitions, and a replication factor of 1 on this one
# broker.
#
# - One topic per event schema (platform/event-schemas/schemas/<topic>.json), mounted at /schemas.
#   Each is log-compacted, so replaying it yields the latest event of every aggregate, which is what
#   a rebuilt projection needs.
# - A dead-letter topic, <group>.dlt, and a redrive topic, <group>.redrive, per consumer group in
#   /consumer-groups (infra/docker/consumer-groups). They carry other topics' events as they were
#   read, so they have no schema; they aren't compacted, and keep events for 14 days.
set -eu

# create <topic> <option>...
create() {
  topic=$1
  shift
  /opt/kafka/bin/kafka-topics.sh --bootstrap-server kafka:19092 --create --if-not-exists \
    --topic "$topic" --partitions 6 --replication-factor 1 "$@"
}

for schema in /schemas/*.json; do
  topic=$(basename "$schema" .json)
  create "$topic" --config cleanup.policy=compact
done
grep -v '^#' /consumer-groups | while read -r group; do
  for topic in "$group.dlt" "$group.redrive"; do
    create "$topic" --config cleanup.policy=delete --config retention.ms=1209600000
  done
done
/opt/kafka/bin/kafka-topics.sh --bootstrap-server kafka:19092 --describe
