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

### Option 1 — Expand the claim/submission status enums *(SELECTED)*

Add lifecycle stages directly to the status enums, as in [`inquest-flow.md`](./inquest-flow.md), e.g.
submission `CREATED → READY_FOR_INITIAL_VALIDATION → INITIAL_VALIDATION_IN_PROGRESS →
INITIAL_VALIDATION_FAILED / READY_FOR_FINAL_VALIDATION → VALIDATION_IN_PROGRESS →
VALIDATION_FAILED / VALIDATION_SUCCEEDED`, with `DISCARDED` / `ABANDONED` terminal draft states.

- **Pros:** single field to read; explicit; easy to query "what stage is this in"; matches the agreed
  flowchart.
- **Cons (and mitigations):** risks conflating *lifecycle* with *validation result* — **mitigated**
  by keeping `inquest_data_required` as a separate flag and leaving terminal `VALID`/`INVALID`
  semantics unchanged; every new value must be added to **two** DBs' CHECK constraints (and,
  deliberately, kept **out** of the reporting materialized views) — accepted as a one-off migration
  cost; `DerivedClaimStatus` derivation must be extended for the new non-terminal states.

### Option 2 — Keep `VALID`/`INVALID` as the validation *result* and model draft/stage separately *(not selected)*

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

**Adopt Option 1 — explicit per-stage lifecycle statuses — aligned to the agreed
[`inquest-flow.md`](./inquest-flow.md).** The initial-validation lifecycle, the draft-hold and the
terminal draft states are represented directly as `SubmissionStatus` / `ClaimStatus` values.
"Additional information required" remains a **flag** (not a status), validation messages are
**stage-tagged**, and a friendly value is exposed to the UI via the existing derived-status
mechanism. Option 2's concerns (reporting-whitelist churn, `DerivedClaimStatus` derivation) are
**mitigated** by: keeping the terminal `VALID`/`INVALID` result semantics unchanged; leaving the new
non-terminal states out of the reporting whitelist by default; and deriving the UI label rather than
overloading the raw status.

### 1. Claim status (`ClaimStatus`)

Proposed values: `READY_TO_PROCESS, READY_FOR_FINAL_VALIDATION, VALID, INVALID, VOID, DISCARDED,
ABANDONED`.

- `READY_TO_PROCESS` — created by parsing; awaiting initial validation (unchanged meaning).
- `READY_FOR_FINAL_VALIDATION` — **new.** Passed initial validation, part of a draft; may still be
  awaiting inquest data. This is the draft-holding state (previously discussed as `READY_FOR_SUBMISSION`
  / "DRAFT").
- `VALID` / `INVALID` — **result of final validation** (unchanged terminal meaning). `INVALID` is also
  the result of an initial-validation error that forces a new submission.
- `VOID` — unchanged.
- `DISCARDED` — **new.** Provider discarded the draft.
- `ABANDONED` — **new.** Draft expired without provider action (subject to the wait-period open
  question below).

### 2. "Inquest data required" is a flag, not a status

Add a boolean **`inquest_data_required`** (true / false / null) to `claim`. Per the flowchart it is
set by the **Fee Scheme Platform (FSP)**, which identifies inquest claims during the initial stage;
it drives the front-end To-Do list and is **orthogonal** to `claim_status`. It is cleared when the
provider supplies valid inquest data. If it is still `true` when the provider submits, it causes a
**validation ERROR at the final stage**. Keeping it a flag avoids a combinatorial explosion of
statuses and reuses the raw-vs-flag pattern that already feeds `DerivedClaimStatus`.

> Generalisation: if the capability extends beyond inquest, replace this with a generic
> `requires_additional_information` boolean plus an `additional_information_type` discriminator. The
> flowchart is currently inquest-specific; see follow-ups.

### 3. Submission status (`SubmissionStatus`)

Proposed additions: `READY_FOR_INITIAL_VALIDATION`, `INITIAL_VALIDATION_IN_PROGRESS`,
`INITIAL_VALIDATION_FAILED`, `READY_FOR_FINAL_VALIDATION` (draft-hold), `DISCARDED`, `ABANDONED`.
Existing `CREATED, VALIDATION_IN_PROGRESS, VALIDATION_SUCCEEDED, VALIDATION_FAILED, REPLACED` are
retained; `VALIDATION_IN_PROGRESS`/`VALIDATION_SUCCEEDED`/`VALIDATION_FAILED` now scope to the
**final** stage. The existing `READY_FOR_VALIDATION` is effectively split into the initial/final
`READY_FOR_*` states — decide whether to retire it or map it to `READY_FOR_FINAL_VALIDATION` (see
migration notes).

### 4. Validation stage is recorded on the message, not (only) inferred from status

Add an explicit **`stage`** value (`INITIAL` | `FINAL`) to `validation_message_log` (or a controlled
`source` convention). This answers *"how are validation errors associated with each stage"*
unambiguously and lets the inquest concern be non-blocking at INITIAL and a **blocking ERROR at
FINAL** without losing its provenance.

### 5. UI label via derived status

Extend the derived/business status (as per `derived-claim-status.md`) to render "Draft",
"Discarded", "Abandoned", "Submitted", etc., so the **raw** statuses stay stable for machine
consumers while the UI gets friendly labels. Do not let the frontend hard-code lifecycle logic on
raw enums.

### 6. Duplicate rules

Claims/submissions in `READY_FOR_FINAL_VALIDATION` **are** considered for duplicate checks;
`DISCARDED` and `ABANDONED` are **not**. Update `DuplicateClaimValidation` (currently
`List.of(READY_TO_PROCESS, VALID)`) accordingly.

---

## Consequences

### API (`laa-data-claims-api`)
- OpenAPI enum additions (`ClaimStatus`, `SubmissionStatus`) → regenerate models; treat as an
  additive, backwards-compatible schema change and version accordingly.
- New Flyway migrations to widen `chk_claim_status` and `chk_submission_status`; add
  `inquest_data_required` (or generic `requires_additional_information` + `additional_information_type`)
  to `claim`; add `stage` to `validation_message_log`.
- New service transitions: parse → `READY_FOR_INITIAL_VALIDATION`; initial-validation completion →
  `READY_FOR_FINAL_VALIDATION`; submit → final validation; discard → `DISCARDED`; timeout →
  `ABANDONED`.

### Event service (`laa-data-claims-event-service`)
- Split validators into **INITIAL** vs **FINAL** rule sets and tag emitted messages with `stage`.
- Integrate FSP inquest **identification** at the initial stage (sets `inquest_data_required`), and
  escalate a still-set flag to a blocking ERROR at FINAL.
- Update duplicate strategy for the new statuses (include `READY_FOR_FINAL_VALIDATION`, exclude
  `DISCARDED`/`ABANDONED`).
- **New capability required:** a timeout/scheduler to move stale drafts to `ABANDONED` — none exists
  today (all flows are SQS-driven). Decide mechanism, period and idempotency.

### Frontend (`laa-submit-a-bulk-claim`)
- New screens/flows: To-Do list (driven by `inquest_data_required`), inquest-data entry,
  Submit and Discard actions.
- New status→label mappings and i18n; render draft/discarded/abandoned/submitted via derived status,
  not raw enums.
- Polling (meta-refresh) now spans two async windows (initial and final validation).

### Reporting (`laa-data-claims-reporting-service`) — **highest backwards-compat risk**
- The reporting DB has its **own** `CHECK` constraints and **whitelisting** materialized views.
  New claim/submission statuses must be added to the reporting `CHECK` constraints or ingestion will
  fail.
- Drafts/discarded/abandoned/initial-stage states should be **deliberately excluded** from
  REP000/012/013/014 (they must not appear in financial reports). Because the views already whitelist
  `VALIDATION_SUCCEEDED`/`VALID`/`VOID`, the default behaviour is exclusion — but this must be
  **verified per report**, not assumed.
- Confirm and document how the reporting DB is fed (replication/events) and update that pipeline.

### Migration / backwards compatibility
- Additive enum values only; no existing value changes meaning (terminal `VALID`/`INVALID` and
  `VALIDATION_SUCCEEDED` semantics are preserved, which is what keeps the reporting whitelist safe).
- Ship DB constraint widening **before** any service emits new values.
- Decide the fate of the existing `READY_FOR_VALIDATION` (retire vs map to
  `READY_FOR_FINAL_VALIDATION`).
- Provide an OpenAPI version bump / consumer-contract note; audit operational tooling and dashboards
  that group by status.

---

## Open questions / follow-ups (candidate stories)

1. Confirm generalisation beyond inquest (keep `inquest_data_required`, or adopt a generic
   `requires_additional_information` + type?).
2. Define the exact **INITIAL vs FINAL** rule split (which existing validators run when), and confirm
   FSP inquest identification runs at INITIAL (one FSP call vs a separate lighter call).
3. Resolve the **wait-period expiry** behaviour posed by the flowchart: `ABANDONED`, a
   reminder/notification, or automatic submission — and design the timeout mechanism (scheduler,
   configurable period, idempotency, re-drive).
4. Decide `stage` on `validation_message_log` vs a `source` convention; migrate existing rows.
5. Reconcile draft edits with the existing amendment model (`is_amended`, `has_assessment`,
   `version`, `claim_amendment`) and optimistic-locking behaviour on concurrent edits.
6. Per-report impact sign-off for REP000/012/013/014 and the reporting ingestion pipeline.
7. New/changed events (initial-validation-complete, submitted, discarded, abandoned) and endpoints
   (submit, discard).
8. Decide ownership of inline inquest field validation (front-end vs API — the flowchart leaves this
   open).
9. Decide the fate of the existing `READY_FOR_VALIDATION` submission status.
10. Terminology alignment: "initial file validation" (requirement) == `INITIAL`; "full validation"
    (requirement) == `FINAL` (flowchart/this ADR).

---

## Recommendation summary

Adopt **explicit per-stage lifecycle statuses** (Option 1) as in [`inquest-flow.md`]:
initial-stage statuses (`READY_FOR_INITIAL_VALIDATION`, `INITIAL_VALIDATION_IN_PROGRESS`,
`INITIAL_VALIDATION_FAILED`), a draft-hold (`READY_FOR_FINAL_VALIDATION`), and terminal draft states
(`DISCARDED`, `ABANDONED`) — while keeping terminal `VALID`/`INVALID`/`VALIDATION_SUCCEEDED`
semantics unchanged, modelling `inquest_data_required` as an orthogonal flag, tagging messages with
`stage`, and exposing one friendly value to the UI via derived status. This keeps result semantics
stable for the reporting whitelist and provides enough definition to write implementation stories
without re-designing the model during delivery.
