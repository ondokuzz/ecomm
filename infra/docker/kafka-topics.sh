#!/bin/sh
# Creates one topic per event schema (platform/event-schemas/schemas/<topic>.json), mounted at
# /schemas, then exits. The broker creates no topics itself. Each topic is log-compacted, so
# replaying it yields the latest event of every aggregate, which is what a rebuilt projection needs;
# 6 partitions, and a replication factor of 1 on this one broker. An existing topic is left alone.
set -eu

for schema in /schemas/*.json; do
  topic=$(basename "$schema" .json)
  /opt/kafka/bin/kafka-topics.sh --bootstrap-server kafka:19092 --create --if-not-exists \
    --topic "$topic" --partitions 6 --replication-factor 1 --config cleanup.policy=compact
done
/opt/kafka/bin/kafka-topics.sh --bootstrap-server kafka:19092 --describe
