#!/usr/bin/env bash

set -euo pipefail

: "${FROM_REF:?FROM_REF is required}"
: "${TO_REF:?TO_REF is required}"
: "${GH_TOKEN:?GH_TOKEN is required}"
: "${GITHUB_REPOSITORY:?GITHUB_REPOSITORY is required}"

MARKDOWN_FILE="${RUNNER_TEMP:-/tmp}/release-diff.md"
JSON_FILE="${RUNNER_TEMP:-/tmp}/release-diff.json"
PRS_FILE="${RUNNER_TEMP:-/tmp}/release-diff-prs.jsonl"

echo "Generating release diff"
echo "  From: $FROM_REF"
echo "  To:   $TO_REF"
echo

# ----------------------------------------------------------------------
# Fetch tags/history
# ----------------------------------------------------------------------

echo "Fetching Git history and tags..."

git fetch --tags --force

# ----------------------------------------------------------------------
# Validate refs
# ----------------------------------------------------------------------

if ! git rev-parse --verify "${FROM_REF}^{commit}" >/dev/null 2>&1; then
  echo "::error::Unable to find Git ref '$FROM_REF'"
  exit 1
fi

if ! git rev-parse --verify "${TO_REF}^{commit}" >/dev/null 2>&1; then
  echo "::error::Unable to find Git ref '$TO_REF'"
  exit 1
fi

FROM_SHA=$(git rev-parse "${FROM_REF}^{commit}")
TO_SHA=$(git rev-parse "${TO_REF}^{commit}")

echo "Resolved refs:"
echo "  $FROM_REF -> $FROM_SHA"
echo "  $TO_REF   -> $TO_SHA"
echo

# ----------------------------------------------------------------------
# Same version
# ----------------------------------------------------------------------

if [ "$FROM_SHA" = "$TO_SHA" ]; then

  cat > "$MARKDOWN_FILE" <<EOF
## Release changes

**${FROM_REF} -> ${TO_REF}**

No changes. Both references resolve to the same commit.
EOF

  jq -n \
    --arg from "$FROM_REF" \
    --arg to "$TO_REF" \
    --arg fromSha "$FROM_SHA" \
    --arg toSha "$TO_SHA" \
    '{
      from: $from,
      to: $to,
      from_sha: $fromSha,
      to_sha: $toSha,
      pull_requests: []
    }' > "$JSON_FILE"

  echo "pull-request-count=0" >> "$GITHUB_OUTPUT"
  echo "markdown-file=$MARKDOWN_FILE" >> "$GITHUB_OUTPUT"
  echo "json-file=$JSON_FILE" >> "$GITHUB_OUTPUT"

  cat "$MARKDOWN_FILE" >> "$GITHUB_STEP_SUMMARY"

  exit 0
fi

# ----------------------------------------------------------------------
# Check ancestry
#
# We normally expect:
#
# FROM -------- TO
#
# not:
#
# TO ---------- FROM
#
# ----------------------------------------------------------------------

if ! git merge-base --is-ancestor "$FROM_SHA" "$TO_SHA"; then

  echo "::error::$FROM_REF is not an ancestor of $TO_REF"
  echo "::error::Refusing to generate a potentially misleading release diff."

  exit 1
fi

# ----------------------------------------------------------------------
# Get commits between refs
# ----------------------------------------------------------------------

mapfile -t COMMITS < <(
  git rev-list --reverse "${FROM_SHA}..${TO_SHA}"
)

COMMIT_COUNT="${#COMMITS[@]}"

echo "Found $COMMIT_COUNT commits"

: > "$PRS_FILE"

# ----------------------------------------------------------------------
# Find PR associated with every commit
# ----------------------------------------------------------------------

for SHA in "${COMMITS[@]}"; do

  echo "Checking $SHA"

  gh api \
    -H "Accept: application/vnd.github+json" \
    "/repos/${GITHUB_REPOSITORY}/commits/${SHA}/pulls" \
    --jq '.[] |
      {
        number: .number,
        title: .title,
        url: .html_url,
        merged_at: .merged_at,
        author: .user.login
      }' \
    >> "$PRS_FILE"

done

# ----------------------------------------------------------------------
# Deduplicate PRs
#
# Multiple commits may belong to the same PR.
# ----------------------------------------------------------------------

if [ -s "$PRS_FILE" ]; then

  jq -s '
    unique_by(.number)
    | sort_by(.number)
  ' "$PRS_FILE" > "${JSON_FILE}.prs"

else

  echo '[]' > "${JSON_FILE}.prs"

fi

PR_COUNT=$(jq 'length' "${JSON_FILE}.prs")

# ----------------------------------------------------------------------
# Build structured JSON
# ----------------------------------------------------------------------

jq -n \
  --arg from "$FROM_REF" \
  --arg to "$TO_REF" \
  --arg fromSha "$FROM_SHA" \
  --arg toSha "$TO_SHA" \
  --argjson commitCount "$COMMIT_COUNT" \
  --slurpfile prs "${JSON_FILE}.prs" \
  '{
    from: $from,
    to: $to,
    from_sha: $fromSha,
    to_sha: $toSha,
    commit_count: $commitCount,
    pull_requests: $prs[0]
  }' > "$JSON_FILE"

# ----------------------------------------------------------------------
# Generate Markdown
# ----------------------------------------------------------------------

{
  echo "## Release changes"
  echo
  echo "**${FROM_REF} -> ${TO_REF}**"
  echo
  echo "**Commits:** ${COMMIT_COUNT}"
  echo
  echo "**Pull requests:** ${PR_COUNT}"
  echo

  if [ "$PR_COUNT" -eq 0 ]; then

    echo "No associated pull requests were found."

  else

    echo "### Pull requests"
    echo

    jq -r '
      .pull_requests[]
      | "- \(.url) \(.title)"
    ' "$JSON_FILE"

  fi

} > "$MARKDOWN_FILE"

# ----------------------------------------------------------------------
# Action outputs
# ----------------------------------------------------------------------

echo "pull-request-count=$PR_COUNT" >> "$GITHUB_OUTPUT"
echo "markdown-file=$MARKDOWN_FILE" >> "$GITHUB_OUTPUT"
echo "json-file=$JSON_FILE" >> "$GITHUB_OUTPUT"

# ----------------------------------------------------------------------
# GitHub Actions summary
# ----------------------------------------------------------------------

cat "$MARKDOWN_FILE" >> "$GITHUB_STEP_SUMMARY"

# ----------------------------------------------------------------------
# Console output
# ----------------------------------------------------------------------

echo
echo "Release diff:"
echo

cat "$MARKDOWN_FILE"