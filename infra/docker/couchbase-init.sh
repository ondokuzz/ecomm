#!/usr/bin/env bash
# One-shot, idempotent Couchbase setup: initialise the cluster, then create the catalog bucket.
set -euo pipefail

HOST=couchbase:8091
USER=${COUCHBASE_USERNAME}
PASS=${COUCHBASE_PASSWORD}
BUCKET=${COUCHBASE_BUCKET}

if couchbase-cli server-list -c "$HOST" -u "$USER" -p "$PASS" >/dev/null 2>&1; then
  echo "Cluster already initialised"
else
  couchbase-cli cluster-init -c "$HOST" \
    --cluster-username "$USER" --cluster-password "$PASS" \
    --services data,index,query \
    --cluster-ramsize 512 --cluster-index-ramsize 256 \
    --index-storage-setting default
fi

if couchbase-cli bucket-list -c "$HOST" -u "$USER" -p "$PASS" | grep -qx "$BUCKET"; then
  echo "Bucket $BUCKET already exists"
else
  couchbase-cli bucket-create -c "$HOST" -u "$USER" -p "$PASS" \
    --bucket "$BUCKET" --bucket-type couchbase --bucket-ramsize 256 --wait
fi
