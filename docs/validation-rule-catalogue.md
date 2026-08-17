# Validation-rule catalogue — INITIAL vs FULL stage

Companion to [ADR-0001](./adr-0001-draft-submission-and-two-stage-validation.md). This catalogue
takes the **existing** validators in `laa-data-claims-event-service` and proposes which stage each
rule runs at under the two-stage model:

- **INITIAL** — can be checked against the uploaded file alone (format, schema, in-memory registries,
  self-contained date/field rules). Runs immediately after parsing. A blocking ERROR here stops the
  claim/submission becoming a draft.
- **FULL** — needs additional information supplied by the provider, and/or external services
  (Provider Details API, Fee Scheme Platform), and/or cross-submission state (duplicate checks).
  Runs when the provider submits the draft.

Severity today is taken from `ClaimValidationError` / `SubmissionValidationError`
(all currently `ERROR`) and the WARNING path used for Fee Scheme messages and (new) missing
additional information. The recommended change is to tag every emitted message with an explicit
**`stage`** (`INITIAL` | `FULL`) on `validation_message_log` so the same rule can be a WARNING at
INITIAL and a blocking ERROR at FULL.

> Classification driver: a validator is **INITIAL-capable** if its only inputs are the claim/
> submission payload plus in-memory config (JSON schema, regex, registries). It is **FULL-only** if
> it injects an external service or reads other submissions/claims.

---

## Submission-level rules

| Rule / validator | What it checks | External dep? | Stage | Severity | Source |
|------------------|----------------|---------------|-------|----------|--------|
| `SubmissionStatusValidator` (prio 1) | Submission is in a state that may be validated; drives `READY_FOR_VALIDATION → VALIDATION_IN_PROGRESS` | No | **Both** (gate) | ERROR (`INCORRECT_SUBMISSION_STATUS_FOR_VALIDATION`, `SUBMISSION_STATUS_IS_NULL`) | EVENT_SERVICE |
| `SubmissionSchemaValidator` (prio 10) | Submission JSON schema | No | **INITIAL** | ERROR | EVENT_SERVICE |
| `SubmissionPeriodValidator` (prio 10) | Period present; `MMM-YYYY` format; not current/future month; ≥ minimum period | No | **INITIAL** | ERROR (`SUBMISSION_PERIOD_MISSING`, `_INVALID_FORMAT`, `_SAME_MONTH`, `_FUTURE_MONTH`, `SUBMISSION_VALIDATION_MINIMUM_PERIOD`) | EVENT_SERVICE |
| `NilSubmissionValidator` (prio 10) | Nil submission has no claims / non-nil has ≥1 claim | No | **INITIAL** | ERROR (`INVALID_NIL_SUBMISSION_CONTAINS_CLAIMS`, `NON_NIL_SUBMISSION_CONTAINS_NO_CLAIMS`) | EVENT_SERVICE |
| `SubmissionOfficeAreaOfLawAndPeriodValidator` (prio 100) | Duplicate submission (same office + area of law + period) | **Yes** — cross-submission lookup | **FULL** | ERROR (`SUBMISSION_ALREADY_EXISTS`) | EVENT_SERVICE |

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

### FULL-only (external service or cross-submission state)

| Rule / validator | What it checks | External dep | Severity | Source |
|------------------|----------------|--------------|----------|--------|
| `EffectiveCategoryOfLawClaimValidator` | Category of law resolvable for fee code; provider contracted for it; area of law valid for provider | `CategoryOfLawValidationService`, `ProviderDetailsService` | ERROR (`INVALID_CATEGORY_OF_LAW_AND_FEE_CODE`, `INVALID_CATEGORY_OF_LAW_NOT_AUTHORISED_FOR_PROVIDER`, `INVALID_AREA_OF_LAW_FOR_PROVIDER`, `TECHNICAL_ERROR_PROVIDER_DETAILS_API`) | EVENT_SERVICE |
| `DuplicateClaimValidator` (+ area-of-law strategies) | Duplicates within this submission and across other/previous submissions | Cross-submission lookups | ERROR (`INVALID_CLAIM_HAS_DUPLICATE_IN_EXISTING_SUBMISSION`, `_IN_ANOTHER_SUBMISSION`) | EVENT_SERVICE |
| Fee calculation (`FeeCalculationService`) | Fee Scheme Platform calculation + its own messages | Fee Scheme Platform API | ERROR **or** WARNING (FSP-driven), plus `INVALID_FEE_CALCULATION_VALIDATION_FAILED`, `TECHNICAL_ERROR_FEE_CALCULATION_SERVICE` | FEE_SERVICE |

> `DuplicateClaimValidator` must be updated for the new statuses (consider `READY_FOR_SUBMISSION`,
> exclude `DISCARDED`/`ABANDONED`) — see ADR §Decision.6.

---

## New rules introduced by draft submission

| Rule | Stage behaviour | Severity | Notes |
|------|-----------------|----------|-------|
| **Additional information required** (generalises inquest) | Emitted at **INITIAL** when required info is absent | **WARNING** at INITIAL | Sets `requires_additional_information = true`; claim held as `READY_FOR_SUBMISSION` and shown on the To-Do list. Non-blocking so the claim can enter draft. |
| **Additional information complete** (escalation) | Re-checked at **FULL** | **ERROR (blocking)** at FULL | If `requires_additional_information` is still true at submit, the same concern escalates to a blocking ERROR → claim `INVALID`, submission `VALIDATION_FAILED`. |
| **Inline additional-info field validation** | On each To-Do save (frontend + API) | ERROR (inline) | Field-level validation of the data the provider enters (format/mandatory). On pass, clears the flag for that claim. Not persisted as submission validation messages unless desired. |

---

## Stage → status mapping (summary)

| Point in flow | Claim status | Submission status | Message stage |
|---------------|--------------|-------------------|---------------|
| After parse | `READY_TO_PROCESS` | `CREATED` | — |
| INITIAL blocking ERROR | `INVALID` | `VALIDATION_FAILED` | `INITIAL` |
| INITIAL passes (info may be missing) | `READY_FOR_SUBMISSION` | `READY_FOR_SUBMISSION` | `INITIAL` (WARNINGs) |
| Provider submits | `READY_FOR_SUBMISSION` → validating | `READY_FOR_VALIDATION` → `VALIDATION_IN_PROGRESS` | — |
| FULL blocking ERROR | `INVALID` | `VALIDATION_FAILED` | `FULL` |
| FULL passes | `VALID` | `VALIDATION_SUCCEEDED` | `FULL` |

---

## Open questions / follow-ups

1. Confirm the INITIAL/FULL split per validator above with the domain team — in particular whether
   any INITIAL-capable rule should be deferred to FULL (e.g. if it depends on a field the provider
   may still be editing).
2. Decide how FSP fee-calculation WARNINGs behave in a draft (surfaced at INITIAL for information,
   re-run at FULL?).
3. Confirm the `stage` field vs a `source` convention on `validation_message_log`, and how existing
   rows are back-filled (see ADR follow-up 4).
4. Define the inline additional-info field-validation rules per additional-info type (first type:
   inquest).
5. Decide whether INITIAL runs the external-dependency rules opportunistically (best-effort, as
   WARNINGs) or defers them entirely to FULL.
