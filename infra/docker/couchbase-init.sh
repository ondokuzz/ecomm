#!/usr/bin/env bash
# One-shot, idempotent Couchbase setup: initialise the cluster, then create the catalog bucket.
set -euo pipefail

HOST=couchbase:8091
USER=${COUCHBASE_USERNAME}
PASS=$(cat /run/secrets/couchbase-password)
BUCKET=${COUCHBASE_BUCKET}

# Couchbase's healthcheck passes on its UI page before its REST API answers, so a failed call then
# said nothing about the cluster, and taking it for "not initialised" made cluster-init fail on an
# initialised one (2026-10-21). /pools needs no credentials and answers which it is: 200 with no
# pools before cluster-init, 401 after it. Wait for one of the two.
state=
for _ in $(seq 60); do
  code=$(curl -s -o /tmp/pools -w '%{http_code}' "http://$HOST/pools" || true)
  if [ "$code" = 401 ]; then
    state=initialised
    break
  elif [ "$code" = 200 ] && grep -q '"pools":\[\]' /tmp/pools; then
    state=new
    break
  fi
  sleep 2
done

case "$state" in
  initialised) echo "Cluster already initialised" ;;
  new)
    couchbase-cli cluster-init -c "$HOST" \
      --cluster-username "$USER" --cluster-password "$PASS" \
      --services data,index,query \
      --cluster-ramsize 512 --cluster-index-ramsize 256 \
      --index-storage-setting default
    ;;
  *)
    echo "Couchbase's REST API didn't say whether the cluster is initialised within 2 minutes" >&2
    exit 1
    ;;
esac

if couchbase-cli bucket-list -c "$HOST" -u "$USER" -p "$PASS" | grep -qx "$BUCKET"; then
  echo "Bucket $BUCKET already exists"
else
  couchbase-cli bucket-create -c "$HOST" -u "$USER" -p "$PASS" \
    --bucket "$BUCKET" --bucket-type couchbase --bucket-ramsize 256 --wait
fi
