#!/usr/bin/env bash
# Converts a Snyk SARIF code scanning analysis into snyk-alerts.json (in the current directory)
# and writes the alert count to $GITHUB_OUTPUT (or stdout locally).
#
# Required env: REPO and ANALYSIS_IDS (comma-separated), or SARIF_FILE to read a local `snyk container test
# --sarif-file-output` report instead of calling GitHub
set -euo pipefail

output_file="${GITHUB_OUTPUT:-/dev/stdout}"

fetch_sarif() {
  if [ -n "${SARIF_FILE:-}" ]; then
    # Same severity fix-up the shared ecr-publish-image workflow applies before uploading to GitHub
    jq '
      .runs[].tool.driver.rules[] |= (
        if (.properties["security-severity"] // "") | tostring | test("^(|null|undefined)$")
        then .properties["security-severity"] =
          (if .shortDescription.text | test("(?i)critical") then "9.0"
           elif .shortDescription.text | test("(?i)high") then "7.0"
           elif .shortDescription.text | test("(?i)medium") then "4.0"
           elif .shortDescription.text | test("(?i)low") then "0.1"
           else "" end)
        else . end)
    ' "$SARIF_FILE"
  else
    : "${REPO:?REPO is required}" "${ANALYSIS_IDS:?ANALYSIS_IDS is required}"
    local id
    for id in ${ANALYSIS_IDS//,/ }; do
      gh api -H "Accept: application/sarif+json" "/repos/$REPO/code-scanning/analyses/$id"
    done | jq -s '{runs: [.[].runs[]]}'
  fi
}

fetch_sarif |
  jq '
    [.runs[] |
      .tool.driver.rules as $rules |
      (.results // [])[] |
      . as $result |
      (if .ruleIndex != null then $rules[.ruleIndex]
       else [$rules[] | select(.id == $result.ruleId)][0] end) as $rule |
      if $rule == null then error("Missing SARIF rule for a finding") else
      (($rule.properties["security-severity"] | tonumber?) // 0) as $score |
      {
        rule: {
          id: ($result.ruleId // $rule.id),
          description: ($rule.shortDescription.text // $result.message.text // $rule.id),
          security_severity_level:
            (if $score >= 9 then "critical" elif $score >= 7 then "high"
             elif $score >= 4 then "medium" elif $score > 0 then "low"
             else "unknown" end)
        },
        html_url: $result.properties["github/alertUrl"],
        most_recent_instance: {
          location: {
            path: $result.locations[0].physicalLocation.artifactLocation.uri,
            start_line: $result.locations[0].physicalLocation.region.startLine
          }
        }
      } end]
  ' > snyk-alerts.json
alert_count=$(jq 'length' snyk-alerts.json)
echo "count=$alert_count" >> "$output_file"
