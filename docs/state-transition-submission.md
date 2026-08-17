# Submission — state-transition diagram (recommended model)

Companion to [ADR-0001](./adr-0001-draft-submission-and-two-stage-validation.md). Shows the
recommended `SubmissionStatus` lifecycle. Existing values are retained; new draft-related states are
highlighted in the notes.

- `VALIDATION_IN_PROGRESS` now means **FULL** validation in progress.
- The short **INITIAL** validation window is represented at the bulk-submission layer
  (see [bulk-submission diagram](./state-transition-bulk-submission.md)); the submission surfaces the
  outcome as `READY_FOR_SUBMISSION` (draft) or a terminal failure.
- `READY_FOR_SUBMISSION`, `DISCARDED`, `ABANDONED` are new.

```mermaid
stateDiagram-v2
    [*] --> CREATED : created by parser<br/>(event service)

    CREATED --> READY_FOR_SUBMISSION : INITIAL validation passes<br/>(draft; UI label "Draft")
    CREATED --> VALIDATION_FAILED : INITIAL validation<br/>blocking ERROR

    READY_FOR_SUBMISSION --> READY_FOR_VALIDATION : provider selects Submit
    READY_FOR_SUBMISSION --> DISCARDED : provider selects Discard
    READY_FOR_SUBMISSION --> ABANDONED : wait period elapsed

    READY_FOR_VALIDATION --> VALIDATION_IN_PROGRESS : FULL validation started<br/>(VALIDATE_SUBMISSION consumed)

    VALIDATION_IN_PROGRESS --> VALIDATION_SUCCEEDED : no blocking errors<br/>(publish SUBMISSION_VALIDATION_SUCCEEDED)
    VALIDATION_IN_PROGRESS --> VALIDATION_FAILED : any blocking ERROR<br/>(all claims -> INVALID)

    VALIDATION_SUCCEEDED --> REPLACED : superseded by a later submission
    VALIDATION_FAILED --> [*] : correction requires a new submission
    VALIDATION_SUCCEEDED --> [*]
    REPLACED --> [*]
    DISCARDED --> [*]
    ABANDONED --> [*]

    note right of CREATED
      NIL submissions created via the API
      short-circuit directly to
      VALIDATION_SUCCEEDED (unchanged).
    end note
```

## Status reference

| Status | New? | Meaning | Reportable? |
|--------|------|---------|-------------|
| `CREATED` | existing | Created by parser; awaiting initial validation | No |
| `READY_FOR_SUBMISSION` | **new** | Passed initial validation; draft awaiting provider action | No |
| `READY_FOR_VALIDATION` | existing | Submitted by provider; queued for full validation | No |
| `VALIDATION_IN_PROGRESS` | existing (re-scoped to FULL) | Full validation running | No |
| `VALIDATION_SUCCEEDED` | existing | Full validation passed; downstream/reporting | **Yes** |
| `VALIDATION_FAILED` | existing | Blocking ERROR at initial or full validation | No |
| `REPLACED` | existing | Superseded by a later submission | No |
| `DISCARDED` | **new** | Provider discarded the draft | No |
| `ABANDONED` | **new** | Draft expired without action | No |

## Notes

- Reporting materialized views whitelist `submission_status = 'VALIDATION_SUCCEEDED'`, so the new
  draft states are excluded by default — **verify per report** and add the new values to the
  reporting DB `CHECK` constraints so ingestion does not fail.
- Events: `READY_FOR_VALIDATION` triggers `VALIDATE_SUBMISSION`; `VALIDATION_SUCCEEDED` triggers
  `SUBMISSION_VALIDATION_SUCCEEDED`. New events may be needed for submit/discard/abandon —
  see ADR follow-ups.
- `ABANDONED` requires a timeout/scheduler that does not exist today (all transitions are SQS-driven).
```
