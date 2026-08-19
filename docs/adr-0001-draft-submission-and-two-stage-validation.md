# ADR-0001: Draft submission, initial validation and full validation status model

- **Status:** Proposed (spike outcome — recommendation for review)
- **Date:** 2026-08-14
- **Deciders:** Submit a Bulk Claim / Data Claims team
- **Related:** [`inquest-flow.md`](./inquest-flow.md), [`derived-claim-status.md`](./derived-claim-status.md),
  state-transition diagrams:
  [claim](./state-transition-claim.md),
  [submission](./state-transition-submission.md),
  [bulk-submission](./state-transition-bulk-submission.md);
  [validation-rule catalogue](./validation-rule-catalogue.md)

---

## Context

The Submit a Bulk Claim service currently allows a provider to upload a bulk claim file. The file is
parsed asynchronously, split into individual claims, and each claim is validated in a **single pass**.

We want to introduce a **draft submission** capability: a provider uploads a file, then provides or
amends additional information (the first concrete case is missing *inquest* details) before
completing the submission. Validation therefore splits into **two stages**:

1. **Initial validation** — everything that can be checked against the uploaded file immediately
   after upload/parse.
2. **Full validation** — validation of the complete claim, including additional information supplied
   after upload, run when the provider submits.

### As-built today (established during the spike)

Source of truth for statuses is the OpenAPI spec in `laa-data-claims-api`
(`claims-data/api/open-api-specification.yml`), from which the enums are generated.

| Entity           | Enum                  | Current values |
|------------------|-----------------------|----------------|
| Claim            | `ClaimStatus`         | `READY_TO_PROCESS, VALID, INVALID, VOID` |
| Submission       | `SubmissionStatus`    | `CREATED, READY_FOR_VALIDATION, VALIDATION_IN_PROGRESS, VALIDATION_SUCCEEDED, VALIDATION_FAILED, REPLACED` |
| Bulk submission  | `BulkSubmissionStatus`| `READY_FOR_PARSING, PARSING_COMPLETED, PARSING_FAILED, VALIDATION_FAILED, REPLACED, UNAUTHORISED, VALIDATION_SUCCEEDED` |
| Claim (derived)  | `DerivedClaimStatus`  | `ACCEPTED, AMENDED, ASSESSED, VOIDED, INVALID, READY_TO_PROCESS` (read-only, computed) |

Key facts that constrain this decision:

- **A raw-vs-derived split already exists.** `DerivedClaimStatus` is a read-only business status
  computed from `claim_status + has_assessment + is_amended`. This is precedent for representing
  business/lifecycle meaning **separately** from the raw processing status.
- **Validation messages already carry severity and provenance.**
  `validation_message_log(type ERROR|WARNING, source, message_code, submission_id, claim_id)`.
  There is currently **no explicit validation *stage*** on a message; `source` is the closest thing.
- **Only ERROR affects status.** In the event service, a claim is `INVALID` only if it has
  submission-level or claim-level ERRORs; WARNINGs are recorded but non-blocking.
- **Transitions are event-driven** via SNS/SQS (`PARSE_BULK_SUBMISSION`, `VALIDATE_SUBMISSION`,
  `SUBMISSION_VALIDATION_SUCCEEDED`). There is **no scheduler/timeout** mechanism anywhere.
- **Status values are hard-coded in three places** and must be kept in lock-step:
  1. OpenAPI enums (API) → generated Java enums, consumed by the frontend and event service.
  2. Flyway `CHECK` constraints in the **API** DB (`chk_claim_status`, `chk_submission_status`,
     `chk_bulk_submission_status`).
  3. The **reporting service**, which has its **own** DB with its **own** `CHECK` constraints and
     materialized views (`mvw_report_000/012/014`, `report_013`) that **whitelist**
     `submission_status = 'VALIDATION_SUCCEEDED' AND claim.status IN ('VALID','VOID')`.
     New statuses are **silently excluded** from reports and will **violate the CHECK constraints**
     unless the reporting migrations are updated too.
- **Duplicate detection** (`DuplicateClaimValidation`) currently considers claims in
  `READY_TO_PROCESS` and `VALID`.
- **Draft / inquest / To-Do / discard / abandon do not exist** anywhere in the four repositories
  today.

---

## Decision drivers

- Represent, unambiguously, at any point in the lifecycle:
  whether initial validation has run; whether it passed; whether the claim is still a draft; whether
  it is ready for full validation; whether full validation passed; and whether it has been submitted.
- Keep existing consumers (reporting, frontend, operational tooling) working, or change them
  **deliberately** rather than by accident.
- Avoid overloading a single status field with orthogonal concepts (lifecycle vs validation result),
  which the two-stage model would otherwise make ambiguous.
- Minimise churn to the (large) reporting whitelist surface.

---

## Options considered

### Option 1 — Expand the claim/submission status enums

Add lifecycle stages directly to the status enums, e.g.
`READY_TO_PROCESS → INITIAL_VALIDATION_IN_PROGRESS → INITIAL_VALIDATION_FAILED / DRAFT_VALID →
FULL_VALIDATION_IN_PROGRESS → INVALID / VALID`.

- **Pros:** single field to read; explicit; easy to query "what stage is this in".
- **Cons:** conflates *lifecycle/draft state* with *validation result*; combinatorial growth
  (draft × passed/failed × stage); every new value must be added to **two** DBs' CHECK constraints
  **and** the reporting materialized views, or rows silently disappear from reports; breaks the
  meaning of the existing terminal `VALID/INVALID` that many consumers assume; invalidates the
  `DerivedClaimStatus` derivation rules.

### Option 2 — Keep `VALID`/`INVALID` as the validation *result* and model draft/stage separately

Retain the raw validation-result statuses and represent draft state, validation stage and
"additional information required" as **separate, orthogonal fields** (plus a small number of new
lifecycle statuses only where genuinely needed). Surface a single friendly value to the UI via a
derived status, exactly as `DerivedClaimStatus` already does.

- **Pros:** orthogonal concepts stay orthogonal; smallest blast radius on the reporting whitelist
  (result semantics unchanged); reuses the existing raw-vs-derived precedent; validation messages
  already support this shape.
- **Cons:** more than one field to reason about; requires a clear derivation/labelling rule so the
  UI and reporting stay consistent.

---

## Decision (recommended)

**Adopt a hybrid that is predominantly Option 2:** model **validation result**, **lifecycle/draft
state** and **stage-tagged validation messages** as separate concepts, and introduce a **small,
explicit** set of new lifecycle statuses for the states that genuinely need to be first-class and
queryable (draft, discarded, abandoned). Expose a single friendly value to the UI through the
existing derived-status mechanism.

### 1. Claim status (`ClaimStatus`) — add a draft-holding state, keep result semantics

Proposed values: `READY_TO_PROCESS, READY_FOR_SUBMISSION, VALID, INVALID, VOID, DISCARDED, ABANDONED`.

- `READY_TO_PROCESS` — created by parsing; awaiting initial validation (unchanged meaning).
- `READY_FOR_SUBMISSION` — **new.** Passed initial validation, part of a draft; may still be
  awaiting additional information. This is the draft-holding state.
- `VALID` / `INVALID` — **result of full validation** (unchanged terminal meaning). `INVALID` is also
  the result of a blocking initial-validation failure that forces a new submission.
- `VOID` — unchanged.
- `DISCARDED` — **new.** Provider discarded the draft.
- `ABANDONED` — **new.** Draft expired without provider action.

### 2. "Additional information required" is a flag, not a status

Add a boolean **`requires_additional_information`** (generalising the flowchart's
`inquest_data_missing`) plus, if needed, a small `additional_information_type` discriminator. This
drives the To-Do list and is **orthogonal** to `claim_status`. It is set true when initial
validation records a "missing info" WARNING and cleared when the provider supplies valid data.
Keeping it a flag avoids a combinatorial explosion of statuses and reuses the raw-vs-flag pattern
that already feeds `DerivedClaimStatus`.

### 3. Submission status (`SubmissionStatus`) — add draft + terminal draft states

Proposed additions: `READY_FOR_SUBMISSION` (draft, initial validation passed),
`DISCARDED`, `ABANDONED`. Existing `CREATED, READY_FOR_VALIDATION, VALIDATION_IN_PROGRESS,
VALIDATION_SUCCEEDED, VALIDATION_FAILED, REPLACED` are retained; `VALIDATION_IN_PROGRESS` now
represents **full** validation in progress. (The initial-validation in-progress window is short and
is represented at the bulk-submission layer — see the bulk-submission diagram — rather than adding
yet another submission state; revisit if initial validation becomes long-running.)

### 4. Validation stage is recorded on the message, not (only) inferred from status

Add an explicit **`stage`** value (`INITIAL` | `FULL`) to `validation_message_log` (or a controlled
`source` convention). This answers *"how are validation errors associated with each stage"*
unambiguously and lets the same rule be a **WARNING at INITIAL** and a **blocking ERROR at FULL**
(the inquest escalation) without losing its provenance.

### 5. UI label via derived status

Extend the derived/business status (as per `derived-claim-status.md`) to render "Draft",
"Discarded", "Abandoned", "Submitted", etc., so the **raw** statuses stay stable for machine
consumers while the UI gets friendly labels. Do not let the frontend hard-code lifecycle logic on
raw enums.

### 6. Duplicate rules

Claims/submissions in `READY_FOR_SUBMISSION` **are** considered for duplicate checks;
`DISCARDED` and `ABANDONED` are **not**. Update `DuplicateClaimValidation` (currently
`List.of(READY_TO_PROCESS, VALID)`) accordingly.

---

## Consequences

### API (`laa-data-claims-api`)
- OpenAPI enum additions (`ClaimStatus`, `SubmissionStatus`) → regenerate models; treat as an
  additive, backwards-compatible schema change and version accordingly.
- New Flyway migrations to widen `chk_claim_status` and `chk_submission_status`; add
  `requires_additional_information` (+ optional `additional_information_type`) to `claim`; add
  `stage` to `validation_message_log`.
- New service transitions: initial-validation completion → `READY_FOR_SUBMISSION`; submit → full
  validation; discard → `DISCARDED`; timeout → `ABANDONED`.

### Event service (`laa-data-claims-event-service`)
- Split validators into **INITIAL** vs **FULL** rule sets and tag emitted messages with `stage`.
- Downgrade "missing additional info" to a WARNING at INITIAL and escalate to a blocking ERROR at
  FULL.
- Update duplicate strategy for the new statuses.
- **New capability required:** a timeout/scheduler to move stale drafts to `ABANDONED` — none exists
  today (all flows are SQS-driven). Decide mechanism, period and idempotency.

### Frontend (`laa-submit-a-bulk-claim`)
- New screens/flows: To-Do list (driven by `requires_additional_information`), additional-info entry,
  Submit and Discard actions.
- New status→label mappings and i18n; render draft/discarded/abandoned/submitted via derived status,
  not raw enums.
- Polling (meta-refresh) now spans two async windows (initial and full validation).

### Reporting (`laa-data-claims-reporting-service`) — **highest backwards-compat risk**
- The reporting DB has its **own** `CHECK` constraints and **whitelisting** materialized views.
  New claim statuses must be added to the reporting `CHECK` constraints or ingestion will fail.
- Drafts/discarded/abandoned should be **deliberately excluded** from REP000/012/013/014 (they must
  not appear in financial reports). Because the views already whitelist
  `VALIDATION_SUCCEEDED`/`VALID`/`VOID`, the default behaviour is exclusion — but this must be
  **verified per report**, not assumed.
- Confirm and document how the reporting DB is fed (replication/events) and update that pipeline.

### Migration / backwards compatibility
- Additive enum values only; no existing value changes meaning.
- Ship DB constraint widening **before** any service emits new values.
- Provide an OpenAPI version bump / consumer-contract note; audit operational tooling and dashboards
  that group by status.

---

## Open questions / follow-ups (candidate stories)

1. Confirm generalisation beyond inquest (is `requires_additional_information` + type sufficient?).
2. Define the exact **INITIAL vs FULL** rule split (which existing validators run when).
3. Design the **ABANDONED** timeout mechanism (scheduler, configurable period, idempotency, re-drive).
4. Decide `stage` on `validation_message_log` vs a `source` convention; migrate existing rows.
5. Reconcile draft edits with the existing amendment model (`is_amended`, `has_assessment`,
   `version`, `claim_amendment`) and optimistic-locking behaviour on concurrent edits.
6. Per-report impact sign-off for REP000/012/013/014 and the reporting ingestion pipeline.
7. New/changed events (initial-validation-complete, submitted, discarded, abandoned) and endpoints
   (submit, discard).
8. Terminology alignment: "initial file validation" (requirement) == `INITIAL` (this ADR).

---

## Recommendation summary

Represent **validation result** (`VALID`/`INVALID`), **lifecycle/draft state**
(`READY_FOR_SUBMISSION`/`DISCARDED`/`ABANDONED` + the existing states), and **stage-tagged messages**
as separate concepts; expose one friendly value to the UI via derived status. This keeps terminal
result semantics stable for the reporting whitelist, mirrors the existing raw-vs-derived precedent,
and provides enough definition to write implementation stories without re-designing the model during
delivery.
