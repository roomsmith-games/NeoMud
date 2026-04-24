#!/usr/bin/env bash
# Block until the most recent GitHub Actions deploy run for HEAD finishes.
#
# Usage:
#   scripts/wait-for-deploy.sh                       # any workflow
#   scripts/wait-for-deploy.sh deploy-maker.yml      # specific workflow
#
# Exit code mirrors the run's conclusion:
#   0 = success
#   non-zero = failure / cancelled / timed out
#
# Designed to be launched via Claude Code's Bash tool with run_in_background=true,
# so a single notification fires the moment the deploy completes — replaces
# guess-the-duration ScheduleWakeup loops.
set -euo pipefail

WORKFLOW="${1:-}"
HEAD_SHA=$(git rev-parse HEAD)

echo "Looking for deploy run on HEAD ${HEAD_SHA:0:8}${WORKFLOW:+ (workflow: $WORKFLOW)}..."

# Run registration can lag push by a few seconds; wait up to 60s.
RUN_ID=""
for _ in $(seq 1 30); do
  RUN_ID=$(gh run list --limit 10 \
    ${WORKFLOW:+--workflow "$WORKFLOW"} \
    --json databaseId,headSha \
    --jq ".[] | select(.headSha==\"$HEAD_SHA\") | .databaseId" | head -1)
  [ -n "$RUN_ID" ] && break
  sleep 2
done

if [ -z "$RUN_ID" ]; then
  echo "No run found for HEAD $HEAD_SHA after 60s" >&2
  exit 2
fi

echo "Watching run $RUN_ID..."
exec gh run watch "$RUN_ID" --exit-status
