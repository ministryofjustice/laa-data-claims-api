# Bulk submission — state-transition diagram (recommended model)

Companion to [ADR-0001](./adr-0001-draft-submission-and-two-stage-validation.md). Shows the
`BulkSubmissionStatus` lifecycle. The bulk-submission layer is where the **INITIAL** validation
window is represented; per-submission and per-claim outcomes are shown in the
[submission](./state-transition-submission.md) and [claim](./state-transition-claim.md) diagrams.

Existing values are retained. Under the two-stage model, `VALIDATION_SUCCEEDED` /
`VALIDATION_FAILED` at this layer reflect the **full** validation outcome; a new
`INITIAL_VALIDATION_FAILED` (optional) makes an initial-stage failure explicit rather than reusing
`VALIDATION_FAILED`.

```mermaid
stateDiagram-v2
    [*] --> READY_FOR_PARSING : file uploaded & authorised

    READY_FOR_PARSING --> PARSING_FAILED : parse error
    READY_FOR_PARSING --> UNAUTHORISED : office not authorised
    READY_FOR_PARSING --> PARSING_COMPLETED : split into submission + claims

    PARSING_COMPLETED --> INITIAL_VALIDATION : INITIAL validation runs
    state INITIAL_VALIDATION <<choice>>
    INITIAL_VALIDATION --> INITIAL_VALIDATION_FAILED : blocking ERROR<br/>(new; provider must re-upload)
    INITIAL_VALIDATION --> DRAFT_READY : passes<br/>(submission -> READY_FOR_SUBMISSION)

    DRAFT_READY --> FULL_VALIDATION : provider submits draft
    DRAFT_READY --> DISCARDED : provider discards
    DRAFT_READY --> ABANDONED : wait period elapsed

    state FULL_VALIDATION <<choice>>
    FULL_VALIDATION --> VALIDATION_SUCCEEDED : no blocking errors
    FULL_VALIDATION --> VALIDATION_FAILED : blocking ERROR

    VALIDATION_SUCCEEDED --> REPLACED : superseded
    PARSING_FAILED --> [*]
    UNAUTHORISED --> [*]
    INITIAL_VALIDATION_FAILED --> [*]
    VALIDATION_FAILED --> [*]
    VALIDATION_SUCCEEDED --> [*]
    REPLACED --> [*]
    DISCARDED --> [*]
    ABANDONED --> [*]
```

> `DRAFT_READY`, `INITIAL_VALIDATION` and `FULL_VALIDATION` above are **modelling helpers** (choice
> pseudo-states / the draft-holding phase), not necessarily new persisted enum values. Whether the
> bulk layer needs its own `INITIAL_VALIDATION_FAILED`, `DISCARDED`, `ABANDONED` values, or whether
> those live only on `submission`/`claim`, is an ADR follow-up (see below).

## Status reference (persisted values)

| Status | New? | Meaning |
|--------|------|---------|
| `READY_FOR_PARSING` | existing | Uploaded, authorised, queued for parsing |
| `PARSING_COMPLETED` | existing | Split into submission + claims; INITIAL validation can run |
| `PARSING_FAILED` | existing | Could not parse the file |
| `UNAUTHORISED` | existing | Office not authorised |
| `INITIAL_VALIDATION_FAILED` | **candidate new** | Blocking failure at initial stage (vs reusing `VALIDATION_FAILED`) |
| `VALIDATION_SUCCEEDED` | existing (FULL) | Full validation passed |
| `VALIDATION_FAILED` | existing (FULL) | Full validation failed |
| `REPLACED` | existing | Superseded by a later bulk submission |
| `DISCARDED` | **candidate new** | Draft discarded (if tracked at bulk layer) |
| `ABANDONED` | **candidate new** | Draft expired (if tracked at bulk layer) |

## Notes / open decisions

- **Where do draft terminal states live?** They must exist on `submission`/`claim`; whether they are
  duplicated onto `bulk_submission` is a follow-up decision. Keeping them off the bulk layer reduces
  enum/constraint churn.
- **Reuse vs new for initial failure:** reusing `VALIDATION_FAILED` for an initial-stage failure is
  cheaper but loses the stage distinction at this layer; `INITIAL_VALIDATION_FAILED` is clearer. The
  per-message `stage` field (see ADR) provides the distinction regardless.
- Any new value must be added to the bulk-submission `CHECK` constraint in **both** the API and
  reporting DBs.
- `ABANDONED` requires a timeout/scheduler that does not exist today.
```
