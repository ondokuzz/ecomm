#!/bin/sh
# Fails if Compose's resolved configuration, every profile's services included, holds any of the
# project's secret values: each must reach its container only as a mounted file. CI runs it after
# `make secrets`; run it from the repo root.
set -eu

dir=.secrets/${COMPOSE_PROJECT_NAME:-ecomm}
config=$(docker compose --profile '*' config)
found=0
checked=0
for file in "$dir"/*; do
  [ -f "$file" ] || continue
  checked=$((checked + 1))
  if printf '%s' "$config" | grep -qF -- "$(cat "$file")"; then
    echo "Compose's resolved configuration holds the value of $file" >&2
    found=1
  fi
done
if [ "$checked" -eq 0 ]; then
  echo "No secrets in $dir: run make secrets first" >&2
  exit 1
fi
[ "$found" -eq 0 ] && echo "None of the $checked secrets in $dir is in Compose's resolved configuration"
exit "$found"
