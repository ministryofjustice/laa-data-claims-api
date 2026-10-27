#!/usr/bin/env bash
# Polls GitHub code scanning until the Snyk Container SARIF analysis for this run has been ingested
# (ingestion is asynchronous), capped at 60s. Writes analysis_ids (comma-separated) to $GITHUB_OUTPUT (or stdout locally).
#
# Required env: REPO, REF, SHA
# CI env:       RUN_ID, RUN_ATTEMPT (used to find when the run started)
# Local env:    RUN_STARTED_AT (ISO 8601) replaces the RUN_ID/RUN_ATTEMPT lookup
set -euo pipefail

: "${REPO:?REPO is required}" "${REF:?REF is required}" "${SHA:?SHA is required}"
output_file="${GITHUB_OUTPUT:-/dev/stdout}"

run_started_at="${RUN_STARTED_AT:-}"
if [ -z "$run_started_at" ]; then
  : "${RUN_ID:?RUN_ID is required unless RUN_STARTED_AT is set}" "${RUN_ATTEMPT:?RUN_ATTEMPT is required unless RUN_STARTED_AT is set}"
  run_started_at=$(gh api \
    -H "Accept: application/vnd.github+json" \
    "/repos/$REPO/actions/runs/$RUN_ID/attempts/$RUN_ATTEMPT" \
    --jq '.run_started_at')
fi
if [ -z "$run_started_at" ] || [ "$run_started_at" = "null" ]; then
  echo "::error::Unable to determine the current run attempt start time"
  exit 1
fi

# One Snyk scan uploads one SARIF file that GitHub splits into several analyses (one per SARIF run,
# each with its own category, all sharing a sarif_id). Wait until every upload from this run reports
# processing_status=complete, then report the newest analysis of each category.
max_attempts=7
delay=10
for i in $(seq 1 "$max_attempts"); do
  analyses=$(gh api --method GET --paginate \
    -H "Accept: application/vnd.github+json" \
    "/repos/$REPO/code-scanning/analyses" \
    -f ref="$REF" -f tool_name="Snyk Container" -f per_page=100 |
    jq -sc --arg sha "$SHA" --arg started "$run_started_at" \
      '[.[][] | select(.commit_sha == $sha and .created_at >= $started)]')
  complete=false
  if [ "$(jq 'length' <<<"$analyses")" -gt 0 ]; then
    complete=true
    for sarif_id in $(jq -r '[.[].sarif_id] | unique | .[]' <<<"$analyses"); do
      status=$(gh api "/repos/$REPO/code-scanning/sarifs/$sarif_id" --jq '.processing_status')
      [ "$status" = "complete" ] || complete=false
    done
  fi
  if [ "$complete" = true ]; then
    analysis_ids=$(jq -r 'group_by(.category) | map(max_by(.created_at, .id).id) | sort | join(",")' <<<"$analyses")
    echo "analysis_ids=$analysis_ids" >> "$output_file"
    echo "SARIF analyses found on attempt $i"
    exit 0
  fi
  if [ "$i" -lt "$max_attempts" ]; then
    echo "Attempt $i/$max_attempts: SARIF processing not complete, waiting ${delay}s"
    sleep "$delay"
  fi
done
echo "::warning::Fresh Snyk SARIF analysis not complete after $(((max_attempts - 1) * delay))s; skipping alert report"
exit 1
