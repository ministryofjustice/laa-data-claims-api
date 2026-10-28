#!/usr/bin/env bash
# Builds snyk-report.md from snyk-alerts.json (both in the current directory) and appends it to
# $GITHUB_STEP_SUMMARY (or stdout locally).
#
# Required env: IMAGE_SCAN_RESULT, REF, ALERT_COUNT
set -euo pipefail

: "${IMAGE_SCAN_RESULT:?IMAGE_SCAN_RESULT is required}" "${REF:?REF is required}" "${ALERT_COUNT:?ALERT_COUNT is required}"
summary_file="${GITHUB_STEP_SUMMARY:-/dev/stdout}"

{
  echo "# Snyk container image vulnerability report"
  echo
  echo "Image scan result: \`$IMAGE_SCAN_RESULT\`"
  echo
  echo "Found **$ALERT_COUNT** open code scanning alert(s) for \`$REF\`."
  echo
  echo "| Severity | Rule | Location | Links |"
  echo "|---|---|---|---|"
  jq -r '
    def sev_rank: {"critical":4,"high":3,"medium":2,"low":1}[.rule.security_severity_level // "unknown"] // 0;
    def sev_emoji: {"critical":"🔴","high":"🟠","medium":"🟡","low":"⚪"}[.rule.security_severity_level // "unknown"] // "⚫";
    sort_by(-sev_rank)[] |
    "| \(sev_emoji) \(.rule.security_severity_level // "unknown" | ascii_upcase) | \(.rule.description // .rule.id) | \(.most_recent_instance.location.path // "n/a"):\(.most_recent_instance.location.start_line // "n/a") | \(if .html_url then "[GitHub](\(.html_url)) / " else "" end)[Snyk](https://security.snyk.io/vuln/\(.rule.id)) |"
  ' snyk-alerts.json
} > snyk-report.md
cat snyk-report.md >> "$summary_file"
