# ADR-0001: Draft submission, INITIAL and FINAL validation status model

- **Status:** Proposed (spike outcome — recommendation for review)
- **Date:** 2026-08-14
- **Deciders:** Submit a Bulk Claim / Data Claims team
- **Related:** [`inquest-flow.md`](../inquest-flow.md), [`derived-claim-status.md`](../derived-claim-status.md),
  state-transition diagrams:
  [claim](../state-transition-claim.md),
  [submission](../state-transition-submission.md),
  [bulk-submission](../state-transition-bulk-submission.md);
  [validation-rule catalogue](../validation-rule-catalogue.md)

---

## Context

The Submit a Bulk Claim service currently allows a provider to upload a bulk claim file. The file is
parsed asynchronously, split into individual claims, and each claim is validated in a **single pass**.

We want to introduce a **draft submission** capability: a provider uploads a file, then **reviews** it
before completing the submission. All data — inquest and non-inquest — arrives in that single upload,
so the **complete claim is available to validate from the outset**. Validation therefore runs in
**two stages over the same complete data**, separated by the review window:

1. **INITIAL validation** — runs immediately after upload/parse, against the complete uploaded claim.
2. **FINAL validation** — runs when the provider submits, re-validating the same complete claim.

The stages are **not** a partial-then-full split: both run the **same full validation over the whole
claim**. The reason for a second pass is the **time lag** — during the review-before-submit window, the
**reference data** the claim is validated against (FSP fee schemes, or PDA provider details) can change,
so FINAL re-runs the identical validation at submission time to catch that. INITIAL and
FINAL differ only in **when** they run, not in which rules execute; neither is a subset of the other.

**Canonical stage names.** This ADR, the flowchart and the requirement wording use the two stage
names **`INITIAL`** and **`FINAL`**. The requirement's "initial file validation" is `INITIAL`; its
"full validation" is `FINAL`. These are the stage names used throughout.

> **Capture-model note (change in direction, 2026-08).** An earlier iteration assumed *inquest* data
> would be supplied **separately after upload**, one claim at a time, via a new "post inquest data"
> endpoint driven by a To-Do list, before completing the submission. That is **no longer the case**:
> **all** data — inquest and non-inquest — now arrives in the **single bulk file upload** and undergoes
> initial validation together. The draft-hold (`READY_FOR_FINAL_VALIDATION`) is therefore a
> **review-before-submit** window, **not** a data-collection step. This *simplifies* the model: the
> two-stage validation, the draft-hold and the terminal draft states below all still stand, but the
> per-claim inquest data-entry sub-flow (To-Do list, per-claim endpoint, and the flag that gated it)
> is removed. See [`inquest-flow.md`](../inquest-flow.md) for the updated flowchart.

> **Feature flag `INQUESTS_ENABLED` (new requirement).** Inquest-field functionality is gated behind a
> feature flag:
> - **Disabled (initial state):** any populated inquest field **fails initial validation** (a blocking
>   ERROR), so providers are not misled into thinking inquest data is being captured yet.
> - **Enabled:** inquest fields are validated per the inquest validation rules (see §2 below).
>
> The flag governs *validation behaviour*, not storage: inquest columns/tables may exist while the flag
> is off; they are simply rejected on input until it is on.

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
- **Only ERROR affects status.** In the event service, a claim is `INVALID` only if it has
  submission-level or claim-level ERRORs; WARNINGs are recorded but non-blocking.
- **Transitions are event-driven** via SNS/SQS (`PARSE_BULK_SUBMISSION`, `VALIDATE_SUBMISSION`,
  `SUBMISSION_VALIDATION_SUCCEEDED`). There is **no scheduler/timeout** mechanism anywhere.
- **Status values are hard-coded and must be kept in lock-step:**
  1. OpenAPI enums (API) → generated Java enums, consumed by the frontend and event service.
  2. Flyway `CHECK` constraints in the **API** DB (`chk_claim_status`, `chk_submission_status`,
     `chk_bulk_submission_status`).
  3. The **reporting service** runs its **own** DB that is a **native Postgres logical-replication
     subscriber** of the API DB (`CREATE SUBSCRIPTION … PUBLICATION claims_reporting_service_pub`,
     `copy_data = true`). Its `submission`/`claim`/`bulk_submission` status `CHECK` constraints were
     **dropped in migration `V8__remove_check_constraints.sql`**, so the replica now accepts **any**
     status value — new statuses **replicate straight through and do not break ingestion**. Its
     materialized views (`mvw_report_000/012/014`, `report_013`) still **whitelist**
     `submission_status = 'VALIDATION_SUCCEEDED' AND claim.status IN ('VALID','VOID')`, so new statuses
     are **silently excluded** from reports (which is the desired behaviour for drafts).
- **Duplicate detection** (`DuplicateClaimValidation`) currently considers claims in
  `READY_TO_PROCESS` and `VALID`.
- **Draft / inquest / To-Do / discard / abandon do not exist** anywhere in the four repositories
  today.

---

## Decision drivers

- Represent, unambiguously, at any point in the lifecycle:
  whether INITIAL validation has run; whether it passed; whether the claim is still a draft; whether
  it is ready for FINAL validation; whether FINAL validation passed; and whether it has been submitted.
- Keep existing consumers (reporting, frontend, operational tooling) working, or change them
  **deliberately** rather than by accident.
- Avoid overloading a single status field with orthogonal concepts (lifecycle vs validation result),
  which the two-stage model would otherwise make ambiguous.
- Minimise churn to the (large) reporting whitelist surface.

---

## Options considered

### Option 1 — Expand the claim/submission status enums *(SELECTED)*

Add lifecycle stages directly to the status enums, as in [`inquest-flow.md`](../inquest-flow.md), e.g.
submission `CREATED → READY_FOR_INITIAL_VALIDATION → INITIAL_VALIDATION_IN_PROGRESS →
INITIAL_VALIDATION_FAILED / READY_FOR_FINAL_VALIDATION → VALIDATION_IN_PROGRESS →
VALIDATION_FAILED / VALIDATION_SUCCEEDED`, with `DISCARDED` / `ABANDONED` terminal draft states.

- **Pros:** single field to read; explicit; easy to query "what stage is this in"; matches the agreed
  flowchart.
- **Cons (and mitigations):** risks conflating *lifecycle* with *validation result* — **mitigated**
  by keeping inquest handling as a separate feature-flagged validation rule (not a status) and leaving
  terminal `VALID`/`INVALID` semantics unchanged; every new value must be added to **two** DBs' CHECK
  constraints (and, deliberately, kept **out** of the reporting materialized views) — accepted as a
  one-off migration cost; `DerivedClaimStatus` derivation must be extended for the new non-terminal
  states.

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
[`inquest-flow.md`](../inquest-flow.md).** The initial-validation lifecycle, the draft-hold and the
terminal draft states are represented directly as `SubmissionStatus` / `ClaimStatus` values.
Inquest handling is a **feature-flagged validation rule** (not a status), and a friendly value is
exposed to the UI via the existing derived-status
mechanism. Option 2's concerns (reporting-whitelist churn, `DerivedClaimStatus` derivation) are
**mitigated** by: keeping the terminal `VALID`/`INVALID` result semantics unchanged; leaving the new
non-terminal states out of the reporting whitelist by default; and deriving the UI label rather than
overloading the raw status.

### 1. Claim status (`ClaimStatus`)

Proposed values: `READY_TO_PROCESS, READY_FOR_FINAL_VALIDATION, VALID, INVALID, VOID, DISCARDED,
ABANDONED`.

- `READY_TO_PROCESS` — created by parsing; awaiting INITIAL validation (unchanged meaning).
- `READY_FOR_FINAL_VALIDATION` — **new.** Passed INITIAL validation; held as a draft pending provider
  **review and submit**. (No longer "awaiting inquest data" — all data arrived in the file.) This is
  the draft-holding state (previously discussed as `READY_FOR_SUBMISSION` / "DRAFT").
- `VALID` / `INVALID` — **result of FINAL validation** (unchanged terminal meaning). `INVALID` is also
  the result of an INITIAL-validation error that forces a new submission.
- `VOID` — unchanged.
- `DISCARDED` — **new.** Provider discarded the draft.
- `ABANDONED` — **new.** Draft expired without provider action (subject to the wait-period open
  question below).

### 2. Inquest handling: a feature-flagged validation rule, not a data-collection flag

Because inquest data now arrives **in the uploaded file** (not entered per claim afterwards), the
former `inquest_data_required` To-Do mechanism is **no longer needed as a data-collection driver**.
Inquest completeness/correctness becomes an ordinary **validation rule**, gated by the
**`INQUESTS_ENABLED`** feature flag:

- **Flag disabled (initial rollout):** if a claim carries **any** populated inquest field, raise a
  **blocking ERROR at INITIAL validation**. This prevents providers from believing inquest data is
  being accepted before the feature is live. The submission cannot progress until the source file is
  corrected (inquest fields removed) and re-uploaded.
- **Flag enabled:** inquest fields are validated per the inquest rules. The **Fee Scheme Platform
  (FSP)** still *identifies* inquest claims (by fee code); that identification now feeds
  **"inquest fields are mandatory for this claim → validate them"** rather than populating a To-Do
  list. Missing/invalid inquest data on an identified inquest claim is a validation error.

A stored **`is_inquest`** boolean is kept on the `claim` record (following the `is_*` boolean
precedent, e.g. `is_amended`, `is_duty_solicitor`, `is_youth_court`; `BOOLEAN NOT NULL DEFAULT FALSE`).
It is **set from FSP identification** (by fee code) — inquest status is data only FSP can determine —
and it **drives inquest validation**: when `true`, the claim's inquest fields are mandatory. It is
**orthogonal** to `claim_status`. This is a claim-type indicator, replacing the former
`inquest_data_required` To-Do flag (which was a data-collection driver, not a claim-type marker).

> Generalisation: if a genuine *post-upload additional-information* case appears later, the removed
> per-claim collection sub-flow (and a stored `requires_additional_information` +
> `additional_information_type`) can be reintroduced additively. It is not required for the
> file-carried inquest case.

### 3. Submission status (`SubmissionStatus`)

Proposed additions: `READY_FOR_INITIAL_VALIDATION`, `INITIAL_VALIDATION_IN_PROGRESS`,
`INITIAL_VALIDATION_FAILED`, `READY_FOR_FINAL_VALIDATION` (draft-hold), `DISCARDED`, `ABANDONED`.
Existing `CREATED, VALIDATION_IN_PROGRESS, VALIDATION_SUCCEEDED, VALIDATION_FAILED, REPLACED` are
retained; `VALIDATION_IN_PROGRESS`/`VALIDATION_SUCCEEDED`/`VALIDATION_FAILED` now scope to the
**final** stage. The existing `READY_FOR_VALIDATION` is **retired** and **replaced** by the two
new stage-specific states `READY_FOR_INITIAL_VALIDATION` and `READY_FOR_FINAL_VALIDATION` (see
migration notes).

### 4. UI label via derived status

Extend the derived/business status (as per `derived-claim-status.md`) to render "Draft",
"Discarded", "Abandoned", "Submitted", etc., so the **raw** statuses stay stable for machine
consumers while the UI gets friendly labels. Do not let the frontend hard-code lifecycle logic on
raw enums.

### 5. Duplicate rules

Claims/submissions in `READY_FOR_FINAL_VALIDATION` **are** considered for duplicate checks;
`DISCARDED` and `ABANDONED` are **not**. Update `DuplicateClaimValidation` (currently
`List.of(READY_TO_PROCESS, VALID)`) accordingly.

---

## Consequences

### API (`laa-data-claims-api`)
- OpenAPI enum additions (`ClaimStatus`, `SubmissionStatus`) → regenerate models; treat as an
  additive, backwards-compatible schema change and version accordingly.
- New Flyway migrations to widen `chk_claim_status` and `chk_submission_status`, and add an
  **`is_inquest` BOOLEAN NOT NULL DEFAULT FALSE** column to `claim` (set from FSP identification).
- New service transitions: parse → `READY_FOR_INITIAL_VALIDATION`; initial-validation completion →
  `READY_FOR_FINAL_VALIDATION`; submit → final validation; discard → `DISCARDED`; timeout →
  `ABANDONED`.
- **Validation-trigger rework (grounded in the code).** Today `SubmissionService.updateSubmission`
  publishes the validation event **only** when a patch sets `READY_FOR_VALIDATION`. Retiring that value
  means this trigger must branch: parse-completion → publish **INITIAL** validation; provider submit →
  publish **FINAL** validation. Keep the "publish after commit" behaviour.
- **NIL submissions bypass the whole staged flow — no draft-hold, no final submission.** Per business
  decision, a NIL submission does **not** enter the draft-hold (`READY_FOR_FINAL_VALIDATION`, i.e. the
  "ready for submission" state) and does **not** require a final submission. The frontend creates NIL
  submissions with a non-`CREATED` status, and `SubmissionService.createSubmission` validates them
  **synchronously** and sets `VALIDATION_SUCCEEDED` directly (no event flow, no review window). When
  `READY_FOR_VALIDATION` is retired, the frontend sets **`READY_FOR_INITIAL_VALIDATION`** on the NIL
  `SubmissionPost`: its single synchronous validation stands in for INITIAL and yields
  `VALIDATION_SUCCEEDED` — it never reaches the draft-hold or a FINAL stage. The existing
  `status != CREATED` guard is unchanged, and the transient value is overwritten to
  `VALIDATION_SUCCEEDED` before persistence, so no NIL row ever rests in an intermediate state.
- **`INQUESTS_ENABLED` feature flag.** Supplied as an **environment variable** injected by Helm from an
  **AWS/Kubernetes secret** (per-environment), read by the **event service** where inquest validation
  runs. Off by default. Its **disabled-state** behaviour is as specified in this ADR (any populated
  inquest field ⇒ blocking ERROR at INITIAL); enabled-state rules are TBD (see open questions).

### Event service (`laa-data-claims-event-service`)
- **All staged validation runs here and is asynchronous.** Both **INITIAL** and **FINAL** validation
  execute in the event service via the existing SQS-driven flow; neither stage runs synchronously in
  the API. (The NIL synchronous validate-and-complete path in the API is a separate special case that
  bypasses staged validation entirely — see the API section.)
- Run the **same full validation at both stages** over the complete claim. INITIAL and FINAL differ
  only in **when** they run (after parse vs at submit), not in which rules execute — there is no
  per-stage rule subset. This includes the external **Fee Scheme Platform (FSP)** call, which is made
  **again at FINAL** for both **inquest identification** and **fee calculation**, so results reflect any
  reference/fee-data changes during the review window.
- **Inquest validation gated by `INQUESTS_ENABLED`:**
  - **Off:** any populated inquest field → **blocking ERROR at INITIAL** ("inquest data not accepted
    yet"). No inquest data is persisted onward from such a submission.
  - **On:** validate inquest fields per the inquest rules; FSP **identification** (by fee code) marks a
    claim as an inquest claim so its inquest fields are mandatory. (The old "set a flag then escalate a
    still-set flag to ERROR at FINAL" mechanic is **removed**.)
- Update duplicate strategy for the new statuses (include `READY_FOR_FINAL_VALIDATION`, exclude
  `DISCARDED`/`ABANDONED`). Concretely, `DuplicateClaimValidation` currently queries submissions in
  `CREATED, VALIDATION_IN_PROGRESS, READY_FOR_VALIDATION, VALIDATION_SUCCEEDED`; swap
  `READY_FOR_VALIDATION` for the two new gate states as appropriate.
- **Validation gate (`SubmissionStatusValidator`, priority 1) must accept both new gates.** It today
  permits validation only from `READY_FOR_VALIDATION`/`VALIDATION_IN_PROGRESS` and does
  `READY_FOR_VALIDATION → VALIDATION_IN_PROGRESS`. Extend it to
  `READY_FOR_INITIAL_VALIDATION → INITIAL_VALIDATION_IN_PROGRESS` **and**
  `READY_FOR_FINAL_VALIDATION → VALIDATION_IN_PROGRESS`; every other state stays an
  `INCORRECT_SUBMISSION_STATUS_FOR_VALIDATION` error. Update the `submission-fields.schema.json` enum
  and its error message too.
- `BulkParsingService` sets the post-parse status; change its final
  `updateSubmission(..., READY_FOR_VALIDATION)` to `READY_FOR_INITIAL_VALIDATION`.
- **New capability required:** a timeout/scheduler to move stale drafts to `ABANDONED` — none exists
  today (all flows are SQS-driven). Decide mechanism, period and idempotency.

### Frontend (`laa-submit-a-bulk-claim`)
- **Removed:** the To-Do list and per-claim inquest-data-entry screens (data now arrives in the file).
- **Retained/new:** a **review** view of the held draft submission, plus **Submit** and **Discard**
  actions. The draft-hold is **strictly review-only** — there is no in-place editing of a held draft;
  any correction requires Discard and a fresh upload (a new submission).
- New status→label mappings and i18n; render draft/discarded/abandoned/submitted via derived status,
  not raw enums.
- Polling (meta-refresh) now spans two async windows (initial and final validation).

### Reporting (`laa-data-claims-reporting-service`)
- **Fed by native Postgres logical replication**, not events: the reporting DB subscribes to the API
  DB's publication (`V4__start_replication.sql`, `copy_data = true`). New draft/discard/abandon rows
  are ordinary status writes that **replicate automatically** — no ingestion code or new events to add.
- **No CHECK-constraint work on the reporting side.** The replica's status `CHECK` constraints were
  **dropped in `V8__remove_check_constraints.sql`**, so new statuses will **not** violate constraints
  or stall replication.
- **New columns/tables, not statuses, are the only pipeline-affecting work.** The new **`is_inquest`**
  column on `claim` (and any inquest-data tables, per ADR-0002) must be added to the **publication** and
  the **replica schema** to flow through to reporting; the status changes in this ADR need nothing here.
- **Report content is auto-excluded; sign-off is verification-only.** Because the views whitelist
  `VALIDATION_SUCCEEDED`/`VALID`/`VOID`, drafts/discarded/abandoned/initial-stage states are already
  excluded from REP000/012/013/014 — confirm **per report**, but no view changes are expected.

### Migration / backwards compatibility
- Enum changes are additive **except** for the retirement of `READY_FOR_VALIDATION` (below); no
  existing value changes meaning (terminal `VALID`/`INVALID` and `VALIDATION_SUCCEEDED` semantics are
  preserved, which is what keeps the reporting whitelist safe).
- Ship **API DB** `CHECK`-constraint widening **before** any service emits new values. The reporting
  replica needs no constraint change (its status constraints were dropped in `V8`).
- **Retire `READY_FOR_VALIDATION`**, replacing it with `READY_FOR_INITIAL_VALIDATION` and
  `READY_FOR_FINAL_VALIDATION`. It exists in the **API DB** `chk_submission_status`, the OpenAPI enum,
  the event-service `submission-fields.schema.json`, and numerous fixtures/tests across all four repos.
  (The reporting replica has no status CHECK constraint to update, but the value will linger in
  replicated rows until they age out.) Migrate any
  in-flight rows: post-parse rows map to `READY_FOR_INITIAL_VALIDATION`; **NIL submissions** also use
  `READY_FOR_INITIAL_VALIDATION` (validated synchronously to `VALIDATION_SUCCEEDED`, so the value never
  persists, and they never enter the draft-hold or a FINAL stage). Drop `READY_FOR_VALIDATION` from both
  CHECK constraints once no rows remain.
- Provide an OpenAPI version bump / consumer-contract note; audit operational tooling and dashboards
  that group by status.

---

## Open questions / follow-ups (candidate stories)

1. Resolve the **wait-period expiry** behaviour posed by the flowchart: `ABANDONED`, a
   reminder/notification, or automatic submission — and design the timeout mechanism (scheduler,
   configurable period, idempotency, re-drive).
2. Per-report content sign-off for REP000/012/013/014 (verify each still excludes the new
   non-terminal/draft statuses — expected, since the views whitelist `VALIDATION_SUCCEEDED`/`VALID`/
   `VOID`). **Reporting ingestion needs no change for statuses** (native logical replication; replica
   status CHECK constraints already dropped in `V8`). The only ingestion work arises **if new columns/
   tables are added** (e.g. the new `is_inquest` column, or inquest-data tables): they must be added to
   the publication and the replica schema to replicate.
3. New/changed events and endpoints. Because **both validation stages are asynchronous in the event
   service**, the new **events** cover an **INITIAL-validation trigger** (on parse completion), an
   **INITIAL-validation-complete** event, and a **FINAL-validation trigger** (on submit) — extending the
   existing SNS/SQS set (`PARSE_BULK_SUBMISSION`, `VALIDATE_SUBMISSION`,
   `SUBMISSION_VALIDATION_SUCCEEDED`) and aligning with the validation-trigger rework in the API
   consequences. New **endpoints**: **submit** (triggers async FINAL validation) and **discard**.
   **`DISCARDED`/`ABANDONED` need no SQS/SNS.** They are set **synchronously** by a transactional status
   write that cascades claims — the same pattern `updateSubmission` already uses for
   `VALIDATION_FAILED → claims INVALID` (via `updateAllClaimsStatusForSubmission`): `DISCARDED` on the
   provider's discard call, `ABANDONED` from the timeout/scheduler mechanism (whose trigger, and any
   reminder/notification side-effect, is parked under the wait-period open question above). **Note:**
   the previously-planned per-claim "post inquest data" endpoint is **no longer required**.
4. **Enabled-state inquest validation rules (`INQUESTS_ENABLED` on).** Define which inquest fields are
   mandatory and their formats etc. The rules apply at **both INITIAL and FINAL** stages.

---

## Recommendation summary

Adopt **explicit per-stage lifecycle statuses** (Option 1) as in [`inquest-flow.md`](../inquest-flow.md):
initial-stage statuses (`READY_FOR_INITIAL_VALIDATION`, `INITIAL_VALIDATION_IN_PROGRESS`,
`INITIAL_VALIDATION_FAILED`), a draft-hold (`READY_FOR_FINAL_VALIDATION`), and terminal draft states
(`DISCARDED`, `ABANDONED`) — while keeping terminal `VALID`/`INVALID`/`VALIDATION_SUCCEEDED`
semantics unchanged, treating inquest handling as a **feature-flagged (`INQUESTS_ENABLED`) validation
rule** rather than a data-collection flag, and exposing one friendly
value to the UI via derived status. All inquest and non-inquest data is captured in the single file
upload, so the draft-hold is a review-before-submit window and no per-claim inquest data-entry sub-flow
is required. This keeps result semantics stable for the reporting whitelist and provides enough
definition to write implementation stories without re-designing the model during delivery.
