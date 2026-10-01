#!/usr/bin/env bash

set -euo pipefail

: "${TITLE:?TITLE is required}"
: "${GITHUB_OUTPUT:?GITHUB_OUTPUT is required}"

JIRA_BASE_URL="${JIRA_BASE_URL:-https://dsdmoj.atlassian.net/browse/}"
TICKET_PATTERN="${TICKET_PATTERN:-[A-Z]+-[0-9]+}"

# Only look at the portion of the title before the first ':', matching this
# repo's '(DSTEW-1234): message' / 'type(DSTEW-1234): message' /
# '[DSTEW-1234] - message' conventions. Anything after the colon is treated as
# free-text description and ignored, so a ticket ID mentioned only in the
# description body is not mistakenly linked.
PREFIX="${TITLE%%:*}"

# If multiple ticket IDs appear (e.g. "test(DSTEW-1774|DSTEW-1646): ..."),
# just take the first one.
TICKET_ID=$(grep -oE "$TICKET_PATTERN" <<< "$PREFIX" | head -n1 || true)

if [ -n "$TICKET_ID" ]; then
  TICKET_URL="${JIRA_BASE_URL}${TICKET_ID}"
else
  TICKET_URL=""
fi

echo "Title:      $TITLE"
echo "Ticket ID:  ${TICKET_ID:-<none>}"
echo "Ticket URL: ${TICKET_URL:-<none>}"

echo "ticket-id=$TICKET_ID" >> "$GITHUB_OUTPUT"
echo "ticket-url=$TICKET_URL" >> "$GITHUB_OUTPUT"

