# Validation-rule catalogue — INITIAL vs FINAL stage

Companion to [ADR-0001](./adr-0001-draft-submission-and-two-stage-validation.md). This catalogue
takes the **existing** validators in `laa-data-claims-event-service` and proposes which stage each
rule runs at under the two-stage model, aligned to [`inquest-flow.md`](./inquest-flow.md):

- **INITIAL** — can be checked against the uploaded file alone (format, schema, in-memory registries,
  self-contained date/field rules). Runs immediately after parsing. A validation ERROR here stops the
  claim/submission becoming a draft (submission `INITIAL_VALIDATION_FAILED`, claims `INVALID`).
- **FINAL** — needs additional information supplied by the provider, and/or external services
  (Provider Details API, Fee Scheme Platform), and/or cross-submission state (duplicate checks).
  Runs when the provider submits the draft. (The requirement calls this "full validation".)

Severity today is taken from `ClaimValidationError` / `SubmissionValidationError`
(all currently `ERROR`) plus the WARNING path used for Fee Scheme messages. Per the updated
flowchart, missing inquest data is **not** modelled as a WARNING: the Fee Scheme Platform (FSP)
**identifies** inquest claims at the initial stage and sets `inquest_data_required = true`. If that
flag is still set when the provider submits, it becomes a **validation ERROR** at FINAL. The
recommended change is to tag every emitted message with an explicit **`stage`**
(`INITIAL` | `FINAL`) on `validation_message_log`.

> Classification driver: a validator is **INITIAL-capable** if its only inputs are the claim/
> submission payload plus in-memory config (JSON schema, regex, registries). It is **FINAL-only** if
> it injects an external service or reads other submissions/claims. **Exception:** the flowchart puts
> FSP inquest **identification** (not full fee calculation) in the initial stage — see the new-rules
> section below.

---

## Submission-level rules

| Rule / validator | What it checks | External dep? | Stage | Severity | Source |
|------------------|----------------|---------------|-------|----------|--------|
| `SubmissionStatusValidator` (prio 1) | Submission is in a state that may be validated; drives `READY_FOR_FINAL_VALIDATION → VALIDATION_IN_PROGRESS` (and the initial-stage gate) | No | **Both** (gate) | ERROR (`INCORRECT_SUBMISSION_STATUS_FOR_VALIDATION`, `SUBMISSION_STATUS_IS_NULL`) | EVENT_SERVICE |
| `SubmissionSchemaValidator` (prio 10) | Submission JSON schema | No | **INITIAL** | ERROR | EVENT_SERVICE |
| `SubmissionPeriodValidator` (prio 10) | Period present; `MMM-YYYY` format; not current/future month; ≥ minimum period | No | **INITIAL** | ERROR (`SUBMISSION_PERIOD_MISSING`, `_INVALID_FORMAT`, `_SAME_MONTH`, `_FUTURE_MONTH`, `SUBMISSION_VALIDATION_MINIMUM_PERIOD`) | EVENT_SERVICE |
| `NilSubmissionValidator` (prio 10) | Nil submission has no claims / non-nil has ≥1 claim | No | **INITIAL** | ERROR (`INVALID_NIL_SUBMISSION_CONTAINS_CLAIMS`, `NON_NIL_SUBMISSION_CONTAINS_NO_CLAIMS`) | EVENT_SERVICE |
| `SubmissionOfficeAreaOfLawAndPeriodValidator` (prio 100) | Duplicate submission (same office + area of law + period) | **Yes** — cross-submission lookup | **FINAL** | ERROR (`SUBMISSION_ALREADY_EXISTS`) | EVENT_SERVICE |

---

## Claim-level rules

### INITIAL-capable (file-only)

| Rule / validator | What it checks | Severity | Source |
|------------------|----------------|----------|--------|
| `ClaimSchemaValidator` | Claim JSON schema (per area of law) | ERROR | EVENT_SERVICE |
| `UniqueFileNumberClaimValidator` | UFN format `DDMMYY/NNN` with a past date | ERROR (`INVALID_DATE_IN_UNIQUE_FILE_NUMBER`) | EVENT_SERVICE |
| `ScheduleReferenceClaimValidator` | Schedule reference regex | ERROR | EVENT_SERVICE |
| `MatterTypeClaimValidator` | Matter type code regex | ERROR | EVENT_SERVICE |
| `StageReachedClaimValidator` | Stage reached code regex | ERROR | EVENT_SERVICE |
| `OutcomeCodeClaimValidator` | Outcome code regex (per area of law) | ERROR | EVENT_SERVICE |
| `CaseDatesClaimValidator` | Case start / concluded / transfer date rules | ERROR | EVENT_SERVICE |
| `ClientDateOfBirthClaimValidator` | Client DOB rules | ERROR | EVENT_SERVICE |
| `DisbursementsClaimValidator` | Disbursement VAT amount rules | ERROR | EVENT_SERVICE |
| `DisbursementClaimStartDateValidator` | Disbursement claim start date | ERROR | EVENT_SERVICE |
| `MandatoryFieldClaimValidator` | Required fields by fee type (via `MandatoryFieldsRegistry` / `ExclusionsRegistry`) | ERROR | EVENT_SERVICE |

> These use only the payload plus in-memory schema/regex/registries, so they can all run at INITIAL.

### FINAL-only (external service or cross-submission state)

| Rule / validator | What it checks | External dep | Severity | Source |
|------------------|----------------|--------------|----------|--------|
| `EffectiveCategoryOfLawClaimValidator` | Category of law resolvable for fee code; provider contracted for it; area of law valid for provider | `CategoryOfLawValidationService`, `ProviderDetailsService` | ERROR (`INVALID_CATEGORY_OF_LAW_AND_FEE_CODE`, `INVALID_CATEGORY_OF_LAW_NOT_AUTHORISED_FOR_PROVIDER`, `INVALID_AREA_OF_LAW_FOR_PROVIDER`, `TECHNICAL_ERROR_PROVIDER_DETAILS_API`) | EVENT_SERVICE |
| `DuplicateClaimValidator` (+ area-of-law strategies) | Duplicates within this submission and across other/previous submissions | Cross-submission lookups | ERROR (`INVALID_CLAIM_HAS_DUPLICATE_IN_EXISTING_SUBMISSION`, `_IN_ANOTHER_SUBMISSION`) | EVENT_SERVICE |
| Fee calculation (`FeeCalculationService`) | Fee Scheme Platform calculation + its own messages | Fee Scheme Platform API | ERROR **or** WARNING (FSP-driven), plus `INVALID_FEE_CALCULATION_VALIDATION_FAILED`, `TECHNICAL_ERROR_FEE_CALCULATION_SERVICE` | FEE_SERVICE |

> `DuplicateClaimValidator` must be updated for the new statuses (consider `READY_FOR_FINAL_VALIDATION`,
> exclude `DISCARDED`/`ABANDONED`) — see ADR §Decision.6.
> **Note:** the flowchart implies FSP is also consulted at the **initial** stage to *identify* inquest
> claims (distinct from the full fee calculation above). Confirm whether one FSP call serves both, or
> whether initial uses a lighter identification call.

---

## New rules introduced by draft submission

| Rule | Stage behaviour | Severity | Notes |
|------|-----------------|----------|-------|
| **Inquest identification (FSP)** | Runs at **INITIAL**, after file validation passes | Flag, not a message | FSP identifies inquest claims and sets `inquest_data_required = true`; claim held as `READY_FOR_FINAL_VALIDATION` and shown on the To-Do list. Non-blocking, so the claim can enter draft. |
| **Inquest data still required (escalation)** | Re-checked at **FINAL** | **ERROR (blocking)** at FINAL | If `inquest_data_required` is still true at submit, it becomes a validation ERROR → claim `INVALID`, submission `VALIDATION_FAILED`. |
| **Inline inquest field validation** | On each To-Do save (front-end or API — TBD) | ERROR (inline) | Field-level validation of the inquest data the provider enters. On pass, sets `inquest_data_required = false`. Ownership (front-end vs API) is an open question in the flowchart. |

> Generalisation: if the capability extends beyond inquest, replace `inquest_data_required` with a
> generic `requires_additional_information` (+ type discriminator) — see ADR.

---

## Stage → status mapping (summary)

| Point in flow | Claim status | Submission status | Message stage |
|---------------|--------------|-------------------|---------------|
| After parse | `READY_TO_PROCESS` | `CREATED` → `READY_FOR_INITIAL_VALIDATION` | — |
| INITIAL running | `READY_TO_PROCESS` | `INITIAL_VALIDATION_IN_PROGRESS` | — |
| INITIAL validation ERROR | `INVALID` | `INITIAL_VALIDATION_FAILED` | `INITIAL` |
| INITIAL passes (FSP flags inquest) | `READY_FOR_FINAL_VALIDATION` | `READY_FOR_FINAL_VALIDATION` | `INITIAL` |
| Provider submits | `READY_FOR_FINAL_VALIDATION` → validating | `VALIDATION_IN_PROGRESS` | — |
| FINAL validation ERROR | `INVALID` | `VALIDATION_FAILED` | `FINAL` |
| FINAL passes | `VALID` | `VALIDATION_SUCCEEDED` | `FINAL` |
| Provider discards | `DISCARDED` | `DISCARDED` | — |
| Wait period elapsed | `ABANDONED` (or auto-submit — TBD) | `ABANDONED` (or auto-submit — TBD) | — |

---

## Open questions / follow-ups

1. Confirm the INITIAL/FINAL split per validator above with the domain team — in particular whether
   any INITIAL-capable rule should be deferred to FINAL (e.g. if it depends on a field the provider
   may still be editing).
2. Clarify the FSP interaction: does inquest **identification** at INITIAL reuse the same FSP call as
   the FINAL fee calculation, or a lighter dedicated call? How do FSP fee-calculation WARNINGs behave
   in a draft?
3. Confirm the `stage` field vs a `source` convention on `validation_message_log`, and how existing
   rows are back-filled (see ADR follow-up 4).
4. Define the inline inquest field-validation rules and decide ownership (front-end vs API — the
   flowchart leaves this open).
5. Decide whether INITIAL runs the external-dependency rules opportunistically (best-effort) or
   defers them entirely to FINAL.
6. Resolve the wait-period expiry behaviour: `ABANDONED`, a reminder/notification, or automatic
   submission (the flowchart poses all three).
