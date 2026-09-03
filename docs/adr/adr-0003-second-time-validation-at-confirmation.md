# ADR-0003: Second-time (final) validation of a draft submission at confirmation time

- **Status:** Proposed (spike outcome — recommendation for review)
- **Date:** 2026-09-03
- **Deciders:** Submit a Bulk Claim / Data Claims team, @Aminur Rouf
- **JIRA:** BC-741
- **Related:**
  [`adr-0001-draft-submission-and-two-stage-validation.md`](./adr-0001-draft-submission-and-two-stage-validation.md)
  (BC-744 — the *status/state model*; this ADR is the *implementation approach* for the final
  validation pass it defines),
  [`adr-0002-inquest-data-storage.md`](./adr-0002-inquest-data-storage.md)

---

## Relationship to ADR-0001 (BC-744) — why this is a separate decision

ADR-0001 decides **what states exist** (the two-stage INITIAL/FINAL validation lifecycle, the
`READY_FOR_FINAL_VALIDATION` draft-hold, terminal `VALID`/`INVALID`). It also decides that **both
stages run the same full validation asynchronously in the event service** — FINAL re-runs the identical
pass, differing from INITIAL only in *when* it runs. What it leaves to follow-up work is the **runtime
mechanics** of the FINAL pass: the "submit" endpoint/events and the abandonment timeout (its open
questions on events/endpoints and on the wait-period timeout).

**BC-741 is that follow-up.** It answers a different question: *given the model in ADR-0001, how is the
second validation pass actually implemented at confirmation time?* The two tickets look similar because
they share the draft-submission domain, but they are **not duplicates** — one is the data model, the
other is the runtime design. This ADR does not re-open the status model.

---

## Context

A draft submission is validated once, asynchronously, at upload time by
`SubmissionValidationService.validateSubmission(...)` in `laa-data-claims-event-service`, triggered by a
`VALIDATE_SUBMISSION` SQS message (`SubmissionListener`). That pass:

1. runs submission-level validators (`SubmissionValidator` list — submission status, schema, nil
   submission) ordered by `priority()`;
2. if there are **no** submission-level errors, calls
   `ClaimValidationService.validateAndUpdateClaims(...)`, which **paginates** over every claim
   (`claim-validation-batch-size` = **100** per page) and, per claim, runs the full `ClaimValidator`
   chain — schema, mandatory fields, dates, effective category of law, **Fee Scheme Platform (FSP) fee
   lookups**, and **duplicate-claim** checks;
3. patches submission/bulk-submission status to `VALIDATION_SUCCEEDED` / `VALIDATION_FAILED`.

Between upload and confirmation a draft can sit for some time (in `READY_FOR_FINAL_VALIDATION`). The
claim data itself cannot change while it is held (the draft-hold is review-only), but the **reference
data** it was validated against at INITIAL can: **FSP fee schemes, or PDA (Provider Details API) data,
may change in the interim.** That is the only way a claim that passed INITIAL can fail FINAL. A later
submission for the same claim period, office code and area of law does **not** put this draft at risk —
because the draft's own contents are considered when duplicate-checking any later submission, it is the
**later** submission that would be rejected as the duplicate, not this one.

### As-built today (established during the spike)

- **Confirmation performs no revalidation.** `laa-submit-a-bulk-claim`'s
  `DraftSubmissionService.submitDraftSubmission(...)` simply PATCHes the submission to
  `VALIDATION_SUCCEEDED` (with a `TODO: Perhaps have event service do this step through a message?`).
  On the API side, `SubmissionService.updateSubmission(...)` treats an incoming
  `VALIDATION_SUCCEEDED` patch as a terminal success and publishes a "validation succeeded" event — it
  does **not** re-run validators. The frontend is already wired to render a
  `DraftConfirmationValidationException` (`ConfirmationProblem` / `ClaimConfirmationError`) if
  confirmation is rejected, so the plumbing to **surface** a failed second-time validation to the user
  largely exists; the validation itself does not.
- **Drafts are excluded from duplicate detection.** `DuplicateClaimValidation` considers previous
  claims only where `SubmissionStatus ∈ {CREATED, VALIDATION_IN_PROGRESS, READY_FOR_VALIDATION,
  VALIDATION_SUCCEEDED}` and `ClaimStatus ∈ {READY_TO_PROCESS, VALID}`. Draft-hold
  (`READY_FOR_FINAL_VALIDATION`) claims would therefore be **invisible** to the duplicate check — a
  later submission for the same period/office/area of law would not be checked against an outstanding
  draft, so both could be accepted.
- **Validation is FSP-heavy.** The per-claim pass calls the FSP for fee details
  (`CategoryOfLawValidationService.getFeeDetailsResponseForAllFeeCodesInClaims`) for every page. For a
  large submission this is the dominant cost and the main reason a synchronous, in-request revalidation
  is risky.
- **All existing validation is SQS-driven and asynchronous.** There is no synchronous validation entry
  point and no scheduler anywhere in the four repositories.

---

## Question

**How should second-time validation of a draft submission be implemented at confirmation time?**

Specifically, weigh:

1. Reusing the upload-time validators (schema, nil-submission, claim validation) **synchronously**
   inside the confirmation request.
2. The same, but **asynchronously**.
3. Whether **extending the duplicate-submission check** (period + office code + area of law) to include
   drafts in `READY_FOR_FINAL_VALIDATION` is needed so later submissions are checked against outstanding
   drafts.
4. Whether a **combination** is the right shape.

---

## Decision drivers

- **Correctness over staleness:** confirmation must not accept a submission whose **reference data**
  (FSP fee schemes, or PDA provider details) has changed since upload in a way that would now make it
  invalid.
- **Reuse, don't fork:** the INITIAL and FINAL passes must run the *same* rule implementations so the
  two stages cannot drift. ADR-0001 mandates exactly this — both stages run the same full validation —
  so FINAL must reuse the INITIAL rule implementations unchanged.
- **Predictable latency & load:** a bulk submission can hold thousands of claims; the pass is FSP- and
  DB-heavy and already paginates. The provider's confirmation click must not block on it, and we must
  not amplify FSP load unnecessarily.
- **Reuse the existing async machinery** (SNS/SQS, `SubmissionListener`, visibility extension, re-drive)
  rather than inventing a synchronous path.
- **Fit ADR-0001's model** and keep the reporting whitelist safe (terminal semantics unchanged).

---

## Options considered

### Option A — Synchronous revalidation inside the confirmation request *(not selected)*

Confirmation calls the existing validators inline (in the API confirm endpoint or the frontend
service) and only transitions the submission if they pass.

- **Pros:** simplest mental model; immediate pass/fail to the user; no new eventing; the frontend
  already renders a synchronous `ConfirmationProblem` response.
- **Cons:** re-runs `ClaimValidationService`'s **paginated, FSP-heavy** loop **in-request** — unbounded
  latency and timeout risk for large submissions; duplicates FSP load; no synchronous validation entry
  point exists today (`ClaimValidationService` is built around async batch update + `BulkClaimUpdater`);
  couples request threads to FSP availability. Rejected as the primary mechanism for anything but the
  smallest submissions.

### Option B — Asynchronous revalidation reusing the upload-time pipeline *(selected, as the revalidation mechanism)*

Confirmation moves the submission into a **final-validation** state (`READY_FOR_FINAL_VALIDATION` →
`VALIDATION_IN_PROGRESS` per ADR-0001) and enqueues the FINAL validation event.
`SubmissionListener` / `SubmissionValidationService` run the **same** validators as at upload; on
completion they patch the submission to `VALIDATION_SUCCEEDED` / `VALIDATION_FAILED`. The FINAL stage is
identified by the submission's **status** (`READY_FOR_FINAL_VALIDATION`), not by a message tag — ADR-0001
does not tag messages or rules with a stage. The frontend already polls draft state, so it
observes the outcome and shows the existing confirmation-error view on failure (a failed FINAL pass
returns the submission to the draft-hold rather than confirming it).

- **Pros:** reuses the proven, paginated, re-drivable async pipeline; no request-thread blocking;
  identical rule implementations across stages (ADR-0001 mandates the same full validation); back-pressure
  and retries already handled by SQS; frontend polling already exists.
- **Cons:** confirmation becomes eventually-consistent (a brief "checking" window) rather than an
  instant yes/no; requires the "submit" endpoint/event and the FINAL transitions from ADR-0001; incurs
  the full FSP cost of a complete re-validation each time (accepted — correctness first).

### Option C — Extend the duplicate check to include `READY_FOR_FINAL_VALIDATION` drafts *(selected, as a complement — not a replacement)*

Add `READY_FOR_FINAL_VALIDATION` (draft-hold) to the submission/claim statuses considered by
`DuplicateClaimValidation` (both the in-submission and previous-submission queries), so that a **later**
submission is rejected if it duplicates the period + office code + area of law of an outstanding draft
(rather than both being accepted).

- **Pros:** makes an outstanding draft visible to the duplicate check, so a later duplicate submission is
  correctly rejected — protecting the earlier draft — instead of both being accepted; cheap (query-scope
  change, no extra FSP calls); useful at **both** upload and confirmation.
- **Cons:** this is a correctness fix for *other* submissions, **not** what governs whether *this* draft
  passes FINAL (that is governed by reference-data changes). Broadening the duplicate scope needs care so
  a draft does not flag *itself*, so an **earlier** draft is not flagged against a **later** one (the
  later submission should fail, not the earlier draft), and so discarded/abandoned drafts are excluded.

---

## Decision (recommended)

**Adopt a combination — Option B as the revalidation mechanism, complemented by Option C.** Concretely:

1. **Run the final pass asynchronously (Option B), reusing the upload-time validators unchanged.**
   Do **not** revalidate synchronously in the confirmation request. Confirmation transitions the
   submission to the FINAL-validation states from ADR-0001 and enqueues the FINAL validation
   event handled by the existing `SubmissionListener` → `SubmissionValidationService` path. The FINAL
   stage is identified by the submission's status, not a message tag. This keeps INITIAL and FINAL on
   one code path and avoids blocking the provider on an FSP-heavy loop. **FINAL re-runs the same full
   validation as INITIAL** (per ADR-0001 — no per-stage rule subset); the draft-hold is review-only, so
   the claim data itself cannot have changed. The pass is re-run in full to catch the only inputs that
   can have moved since INITIAL: **reference data (FSP fee schemes, or PDA provider details)**. A later
   submission cannot turn this draft into a duplicate — the draft is itself considered when
   duplicate-checking later submissions, so those are rejected instead.

2. **Extend duplicate detection to drafts (Option C).** Include `READY_FOR_FINAL_VALIDATION` in the
   statuses `DuplicateClaimValidation` considers (excluding the claim's own draft and its siblings, and
   `DISCARDED` / `ABANDONED`). This ensures a **later** submission is duplicate-checked against an
   outstanding draft — so the later submission is rejected, protecting the earlier draft — and is worth
   doing independently of the FINAL pass.

### Where it runs in the pipeline

- **Trigger:** provider confirms → frontend `DraftSubmissionService` (or, preferably, a new API confirm
  endpoint) transitions the submission out of the draft-hold and publishes the FINAL validation event.
  Replace the current "PATCH straight to `VALIDATION_SUCCEEDED`" shortcut in `DraftSubmissionService`
  (the existing `TODO`) with this transition.
- **Execution:** `laa-data-claims-event-service` — the same `SubmissionValidationService` /
  `ClaimValidationService` used at INITIAL, run in full.
- **Outcome:** success → `VALIDATION_SUCCEEDED` (confirmed/submitted); failure → return to the
  draft-hold with `validation_message_log` rows, surfaced through the **existing**
  `ConfirmationProblem` / `DraftConfirmationValidationException` UI path.

---

## Consequences

### Event service (`laa-data-claims-event-service`)
- Distinguish the FINAL run by the submission's **status** (`READY_FOR_FINAL_VALIDATION`), not by tagging
  messages or rules with a stage (ADR-0001 does not tag messages with stage).
  `SubmissionValidationService` / `ClaimValidationService` run the **same** validators in full at both
  stages.
- Update `DuplicateClaimValidation` to include `READY_FOR_FINAL_VALIDATION` (self/sibling/terminal-draft
  exclusions), and swap the retired `READY_FOR_VALIDATION` for the new gate states from ADR-0001. Update
  `DuplicateClaimValidationTest` and the integration tests
  (`DuplicateClaimsTest`, `SubmissionValidationServiceIntegrationTest`).
- New/extended event type for the FINAL trigger; `SubmissionListener`'s
  `processMessageByType` handles it.

### API (`laa-data-claims-api`)
- Prefer an explicit **confirm** transition over accepting a bare `VALIDATION_SUCCEEDED` patch from the
  UI, so the API — not the frontend — owns the "confirm ⇒ run FINAL validation" rule. `SubmissionService`
  should publish the FINAL validation event on the confirm transition instead of treating the patch as
  terminal success.

### Frontend (`laa-submit-a-bulk-claim`)
- Confirmation becomes **eventually consistent**: show a "checking / being validated" state and poll
  (the draft/`READY_FOR_FINAL_VALIDATION` polling already exists), then render success or the existing
  `ConfirmationProblem` errors. Remove the direct-to-`VALIDATION_SUCCEEDED` shortcut in
  `DraftSubmissionService`.

### Performance
- No request-thread blocking on the FSP-heavy loop. The FINAL pass is one extra upload-equivalent full
  validation, already paginated at `claim-validation-batch-size` = 100 and re-drivable via SQS.

### Migration / backwards compatibility
- Purely additive: reuses existing validators, statuses (from ADR-0001) and the SQS pipeline. Terminal
  `VALID` / `INVALID` / `VALIDATION_SUCCEEDED` semantics are unchanged, so the reporting whitelist is
  unaffected. Broadening the duplicate scope changes which **later submissions** are rejected as
  duplicates, not any stored status value.

---

## Open questions / follow-ups (candidate stories)

1. **Confirm endpoint vs patch:** decide whether the API gains a dedicated confirm endpoint (recommended)
   or continues to key off a status patch; update `DraftSubmissionService` accordingly.
2. **Duplicate-scope safety:** finalise the status set for `DuplicateClaimValidation` (include
   `READY_FOR_FINAL_VALIDATION`; exclude `DISCARDED`/`ABANDONED`; exclude self and sibling drafts within
   the same submission).
3. **Concurrency/idempotency:** guard against double-confirm and against a stale draft being confirmed
   while a competing submission commits (optimistic locking / re-check on the FINAL pass).
4. **UX for eventual consistency:** confirm the polling/`ConfirmationProblem` experience for a FINAL
   failure returning the submission to the draft-hold.
5. **Interaction with `ABANDONED` timeout** (ADR-0001 wait-period open question): ensure a draft cannot
   be confirmed and abandoned concurrently.

---

## Recommendation summary

Second-time validation should be **asynchronous, reusing the existing upload-time validators** on the
proven SQS pipeline (Option B), **complemented by extending duplicate detection to
`READY_FOR_FINAL_VALIDATION` drafts** (Option C). The FINAL pass **re-runs the same full validation as
INITIAL** (per ADR-0001 — no per-stage subset), distinguished only by the submission's status;
correctness is prioritised over trimming FSP cost. Synchronous in-request revalidation (Option A) is
rejected because it would block the provider on an FSP-heavy, paginated loop with unbounded latency.
This is distinct from BC-744/ADR-0001, which fixes the *status model*; BC-741/this ADR fixes *how the
final pass runs at confirmation*.
