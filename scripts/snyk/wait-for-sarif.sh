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

# One Snyk scan uploads several analyses (one per SARIF run, each with its own category), so
# collect the newest analysis of every category and wait until that set stops growing.
max_attempts=7
delay=10
previous=""
for i in $(seq 1 "$max_attempts"); do
  analysis_ids=$(gh api --method GET --paginate \
    -H "Accept: application/vnd.github+json" \
    "/repos/$REPO/code-scanning/analyses" \
    -f ref="$REF" -f tool_name="Snyk Container" -f per_page=100 |
    jq -sr --arg sha "$SHA" --arg started "$run_started_at" \
      '[.[][] | select(.commit_sha == $sha and .created_at >= $started)] |
       group_by(.category) | map(max_by(.created_at, .id).id) | sort | join(",")')
  if [ -n "$analysis_ids" ] && [ "$analysis_ids" = "$previous" ]; then
    echo "analysis_ids=$analysis_ids" >> "$output_file"
    echo "SARIF analyses found on attempt $i"
    exit 0
  fi
  previous="$analysis_ids"
  if [ "$i" -lt "$max_attempts" ]; then
    echo "Attempt $i/$max_attempts: analyses not yet complete, waiting ${delay}s"
    sleep "$delay"
  fi
done
if [ -n "$previous" ]; then
  echo "::warning::Snyk SARIF analyses were still arriving after $(((max_attempts - 1) * delay))s; reporting what has landed"
  echo "analysis_ids=$previous" >> "$output_file"
  exit 0
fi
echo "::warning::Fresh Snyk SARIF analysis not found after $(((max_attempts - 1) * delay))s; skipping alert report"
exit 1
