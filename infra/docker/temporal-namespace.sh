#!/bin/sh
# Creates the namespace every workflow runs in, `default`, keeping a closed workflow's history for 7
# days, or sets that retention on the namespace if it exists. Then exits; runs on every
# `docker compose up`, once the server is up.
set -eu

until temporal operator cluster health >/dev/null 2>&1; do sleep 1; done

if temporal operator namespace describe --namespace default >/dev/null 2>&1; then
  temporal operator namespace update --namespace default --retention 168h
else
  temporal operator namespace create --namespace default --retention 168h
fi
