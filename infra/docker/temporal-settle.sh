#!/bin/sh
# Waits until Orchestration has worked through every running workflow's pending workflow task.
# `infra/docker/warm-up.py` runs it, in the temporal-namespace service's admin-tools image, so the
# first checkout after `make up` doesn't wait behind them (#107). A Saga's timers that came due
# while the stack was down fire as it starts, each a workflow task the cold worker must replay the
# run's history for: about 50 open fulfillment runs did so at once on 2026-10-27, and a checkout's
# Saga waited behind them past the Playwright suites' 15 seconds.
#
# A run whose task has failed more than 3 attempts fails each one again, its code no longer matching
# its history, and is left to the Temporal UI, named here, rather than waited for.
set -eu

query="ExecutionStatus='Running'"
began=$(date +%s)
while :; do
  pending=0
  failing=""
  # Listed first and parsed after, so a failed listing stops the script rather than reading as none.
  listed=$(temporal workflow list --query "$query" -o jsonl)
  for id in $(echo "$listed" | sed -n 's/^{"execution":{"workflowId":"\([^"]*\)".*/\1/p'); do
    # A run may close between the listing and its description.
    description=$(temporal workflow describe -w "$id" -o json) || continue
    attempt=$(echo "$description" |
      sed -n '/"pendingWorkflowTask"/,/}/s/.*"attempt": \([0-9]*\).*/\1/p')
    if [ -z "$attempt" ]; then
      continue
    elif [ "$attempt" -gt 3 ]; then
      failing="$failing $id"
    else
      pending=$((pending + 1))
    fi
  done
  if [ "$pending" -eq 0 ]; then
    break
  fi
  if [ $(($(date +%s) - began)) -ge 300 ]; then
    echo "$pending workflow tasks were still pending after 5 minutes" >&2
    exit 1
  fi
  sleep 2
done
for id in $failing; do
  echo "  $id fails each workflow task; see it in the Temporal UI" >&2
done
