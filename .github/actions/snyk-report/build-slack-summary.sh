#!/usr/bin/env bash
# Writes slack-summary.txt showing the three highest-severity alerts from snyk-alerts.json and links to
# the full GitHub summary, then writes the JSON-escaped text to $GITHUB_OUTPUT (or stdout locally).
#
# Required env: IMAGE_SCAN_RESULT, RUN_URL, ALERTS_OUTCOME
# Optional env: ALERT_COUNT
set -euo pipefail

: "${IMAGE_SCAN_RESULT:?IMAGE_SCAN_RESULT is required}" "${RUN_URL:?RUN_URL is required}" "${ALERTS_OUTCOME:?ALERTS_OUTCOME is required}"
output_file="${GITHUB_OUTPUT:-/dev/stdout}"

scan_result="$IMAGE_SCAN_RESULT"
if [ "$scan_result" = "failure" ]; then
  scan_result="FAILURE"
fi
{
  echo "*Snyk scan:* $scan_result - Open alerts: ${ALERT_COUNT:-unknown} (<$RUN_URL|run>)"
  echo
  if [ "$ALERTS_OUTCOME" = "success" ]; then
    jq -r '
      def sev_rank: {"critical":4,"high":3,"medium":2,"low":1}[.rule.security_severity_level // "unknown"] // 0;
      def sev_emoji: {"critical":"🔴","high":"🟠","medium":"🟡","low":"⚪"}[.rule.security_severity_level // "unknown"] // "⚫";
      sort_by(-sev_rank)[:3][] |
      (.rule.security_severity_level // "unknown" | ascii_upcase) as $sev |
      (.rule.description // .rule.id | sub("^(Low|Medium|High|Critical) severity - "; "")) as $desc |
      "\(sev_emoji) [\($sev)] \($desc) - \(if .html_url then "<\(.html_url)|GitHub> · " else "" end)<https://security.snyk.io/vuln/\(.rule.id)|Snyk>"
    ' snyk-alerts.json
    if [ "${ALERT_COUNT:-0}" -gt 3 ]; then
      echo
      echo "$((ALERT_COUNT - 3)) more alert(s) - <$RUN_URL|View the full list in the GitHub summary>"
    fi
  else
    echo "Vulnerability details unavailable - see workflow run logs"
  fi
} > slack-summary.txt
echo "text=$(jq -Rs . slack-summary.txt)" >> "$output_file"
