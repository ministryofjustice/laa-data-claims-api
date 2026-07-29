# Release / Upgrade Notes — Unique constraint on claim `(submission_id, line_number)`

**Change:** Flyway migration `V44__add_unique_constraint_claim_submission_line_number.sql` adds a
database-level unique constraint `uq_claim_submission_line_number` on `claim (submission_id,
line_number)`.

**Business rule enforced:** a claim's `line_number` must be unique within a submission. Duplicate
`(submission_id, line_number)` combinations can no longer exist.

## API behaviour change

`POST /api/v1/submissions/{id}/claims` now returns **409 Conflict** (previously 500) when the new
claim would duplicate an existing `(submission_id, line_number)`. The RFC 9457 problem detail body
carries the message *"A claim with this line number already exists for the submission."* and a
`type` of `.../errors/data-integrity-violation`. The failed request is fully rolled back (no partial
rows). This is documented in `open-api-specification.yml` on the `createClaim` operation.

The amend endpoint (`PATCH …/claims/{claim-id}`) is unaffected: `line_number` is not an amendable
field, so an attempt to change it is rejected earlier with **400**
(`INVALID_FIELD_NOT_AMENDABLE_FOR_AREA_OF_LAW`) and can never reach the database constraint.

---


## ⚠️ Mandatory pre-deployment check

Flyway applies `V44` inside a transaction. If **any** duplicate `(submission_id, line_number)`
rows already exist, the `ALTER TABLE ... ADD CONSTRAINT` will **fail** and the deployment/migration
will be rolled back.

**Before deploying**, run the following against the target database and confirm it returns
**0 rows**:

```sql
SELECT
    submission_id,
    line_number,
    COUNT(*) AS duplicate_count
FROM claim
GROUP BY submission_id, line_number
HAVING COUNT(*) > 1;
```

- **Who runs it:** the deployer / DBA performing the release, as part of the pre-deploy checklist,
  in each environment being promoted (dev → uat → prod).
- **Expected result:** `0 rows`.

### If duplicates exist

- **Do not deploy.**
- Investigate and correct the data manually with the data owners.
- **Do not** introduce automated cleanup as part of this change. Any automated remediation must be
  proposed and reviewed as a separate ticket.

---

## Post-deployment verification

Confirm the constraint is present:

```sql
SELECT conname
FROM pg_constraint
WHERE conname = 'uq_claim_submission_line_number';
```

Expected: one row.

---

## Rollback

To remove the constraint (e.g. emergency rollback), run:

```sql
ALTER TABLE claim DROP CONSTRAINT IF EXISTS uq_claim_submission_line_number;
```

Note: dropping the constraint re-permits duplicates and should only be done as a deliberate,
reviewed action.

