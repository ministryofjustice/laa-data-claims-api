# Release / Upgrade Notes — Unique `(submission_id, line_number)` on claim

**Change:** Flyway migration `V45__add_unique_constraint_claim_submission_line_number.sql` adds a
**partial unique index** `uq_claim_submission_line_number` on `claim (submission_id, line_number)`,
plus an application-level pre-check and a 409 mapping (belt-and-braces).

**Business rule enforced:** a claim's `line_number` must be unique within a submission. New duplicate
`(submission_id, line_number)` combinations can no longer be created.

## How enforcement works (three layers)

1. **Database partial unique index** (`uq_claim_submission_line_number`) — the authoritative,
   race-safe guard. It is *partial* so environments already holding historical duplicates can adopt
   it without the index build failing (see the cutoff placeholder below).
2. **Application pre-check** in `ClaimService.createClaim` — `existsBySubmissionIdAndLineNumber`
   before insert, throwing `DuplicateClaimException` (409). It queries *all* rows, so it also covers
   the *old-vs-new* case the partial index cannot (a new claim duplicating a grandfathered row).
3. **Exception handler** maps a raw index violation to **409** as a backstop for the rare
   concurrent race.

### Caveats (documented in code)
- **old-vs-new:** the partial index only enforces uniqueness among rows above the cutoff, so it
  cannot catch a new claim that duplicates a *grandfathered* historical row. This is currently
  unreachable (claims are only added to newly-created submissions, never appended to historical
  ones); the application pre-check guards it defensively should that rule change.
- **race:** the pre-check is not atomic with the insert, so a small TOCTOU window remains under
  concurrency. The DB index closes it for the common (post-cutoff) case; the residual race only
  affects the old-vs-new scenario and is considered minimal/acceptable.

## Cutoff placeholder (grandfathering existing duplicates)

The index predicate is `WHERE created_on > '${claim_line_number_uniqueness_cutoff}'`. The placeholder
defaults to the epoch (`1970-01-01 00:00:00+00`), so **clean/fresh databases enforce uniqueness for
all rows**.

> **Environments that already contain duplicates MUST override** the placeholder via the
> `CLAIM_LINE_NUMBER_UNIQUENESS_CUTOFF` environment variable with a timestamp *after which the data
> is known to be clean*, before deploying. Rows at/below the cutoff are grandfathered; rows above it
> are enforced.

## API behaviour change

`POST /api/v1/submissions/{id}/claims` now returns **409 Conflict** (previously 500) when the new
claim would duplicate an existing `(submission_id, line_number)`. The RFC 9457 problem detail body
carries the message *"A claim with line number N already exists for the submission."* The failed
request is fully rolled back (no partial rows). This is documented in `open-api-specification.yml` on
the `createClaim` operation.

The amend endpoint (`PATCH …/claims/{claim-id}`) is unaffected: `line_number` is not an amendable
field, so an attempt to change it is rejected earlier with **400**
(`INVALID_FIELD_NOT_AMENDABLE_FOR_AREA_OF_LAW`) and can never reach the database index.

---


## Pre-deployment check (report; sets the cutoff)

Because the index is partial, the migration will **not** fail on existing duplicates *provided the
cutoff excludes them*. Run the following to (a) see whether duplicates exist and (b) decide the
cutoff value:

```sql
SELECT
    submission_id,
    line_number,
    COUNT(*) AS duplicate_count
FROM claim
GROUP BY submission_id, line_number
HAVING COUNT(*) > 1;
```

- **Who runs it:** the deployer / DBA performing the release, per environment (dev → uat → prod).
- **If it returns 0 rows:** deploy with the default (epoch) cutoff — uniqueness is enforced for all
  rows.
- **If it returns rows:** set `CLAIM_LINE_NUMBER_UNIQUENESS_CUTOFF` to a timestamp after the newest
  duplicate's `created_on` (so all duplicates are grandfathered), then deploy.

### Do not clean up automatically

- Investigate and correct data manually with the data owners only if the business decides to.
- **Do not** introduce automated cleanup as part of this change. Any automated remediation must be
  proposed and reviewed as a separate ticket.

---

## Post-deployment verification

Confirm the unique index is present:

```sql
SELECT indexname, indexdef
FROM pg_indexes
WHERE indexname = 'uq_claim_submission_line_number';
```

Expected: one row — a `CREATE UNIQUE INDEX ... (submission_id, line_number) WHERE ...`.

---

## Rollback

To remove the index (e.g. emergency rollback):

```sql
DROP INDEX IF EXISTS uq_claim_submission_line_number;
```

Note: dropping the index re-permits duplicate creation and should only be done as a deliberate,
reviewed action. The application pre-check remains in force regardless.

