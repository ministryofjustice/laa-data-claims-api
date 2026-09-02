# ADR 0002 — Storage model for Inquest MI data

- **Status:** Proposed — awaiting Payments Data Stewardship (PDS) decision
- **Date:** 2026-08-24
- **Deciders:** Payments Data Stewardship (PDS).
- **Affected services:** `laa-data-claims-api` (owns the schema), `laa-data-claims-event-service` (maps bulk XML → API), `laa-submit-a-bulk-claim` (capture/review UI)
- **Reference branch:** `poc/inquests` on all three repositories
- **Reference migration:** `laa-data-claims-api/claims-data/service/src/main/resources/db/migration/V46__create_inquest_claim_data.sql`

> This ADR is written to double as the **briefing document for PDS**. Sections 1–3 describe what the
> PoC built and why (the briefing). Sections 4–7 lay out the options, a recommendation, and the
> follow-up needed. The final decision on how inquest
> MI data is modelled is PDS's to make and to record here before this ADR moves to *Accepted*.

---

## 1. Context

Inquest ("Article 2 / inquest") claims capture a small set of additional facts that ordinary claims
do not: who the deceased was, key dates, the coroner's inquest reference, and which public bodies are
"interested" in the inquest. During the PoC this data was added to `laa-data-claims-api` under
migration `V46__create_inquest_claim_data.sql` as **four tables**.

Crucially, **this shape was decided inside the PoC, by PoC Developers, without PDS input**. There is no
known problem with it today, but MI/reporting data modelling is PDS's responsibility, and the PoC
schema has not had PDS sign-off. The purpose of this ADR is to put the current model in front of PDS,
present the realistic alternatives, and record the decision (and its rationale) properly — so that
the eventual schema goes through GLAD and the normal deployment process rather than living only as a
PoC artifact.

A concrete, time-sensitive trigger also forces the question now: the governed department list must
change from **24 to 23** entries following the merge of the **Department for Science, Innovation and
Technology (DSIT)**. How we make that change (delete vs. deactivate) depends directly on the storage
model PDS chooses, so it should not be actioned before this decision is recorded.

## 2. Current PoC data model (the briefing)

Four tables, all keyed to `claim(id)`:

### 2.1 `inquest_detail` — 1:1 scalar row per claim
One optional row per claim holding the scalar facts:
`deceased_forename`, `deceased_surname`, `deceased_date_of_birth`, `deceased_date_of_death`,
`coroners_inquest_reference`, plus standard audit columns. Enforced 1:1 by
`claim_id UUID NOT NULL UNIQUE REFERENCES claim(id)`.

### 2.2 `department_reference` — governed lookup of UK government departments
A **governed reference list** of central-government departments. Columns of note:
`code` (stable business code, e.g. `MOJ` — `UNIQUE`), `display_label`, `is_active`,
`display_order` (`UNIQUE`). Seeded by the migration with **24** departments. Serves the
"interested government department" dropdown and lets MI group/label consistently.

### 2.3 `claim_interested_department` — governed link table (many per claim)
One row per (claim, department) pairing. In the PoC the FK is on the **business code**
(`department_code REFERENCES department_reference(code)`), not the surrogate `id`, with
`UNIQUE (claim_id, department_code)`. The FK to the governed list is what makes MI over
departments clean and aggregatable — but note that this benefit comes from having a *governed
FK at all*, **not** from which column it points at. Whether that FK should be the `code` or the
surrogate `id` is a distinct decision with real trade-offs; see §2.8.

### 2.4 `claim_interested_public_authority` — free-text, ordered (many per claim)
One row per interested public authority, stored as **free text** (`authority_name`) with a
`display_order` and `UNIQUE (claim_id, display_order)`. **Deliberately ungoverned.** A migration
comment records the intent that a governed `public_authority_reference` could be added *additively*
later (add an `authority_code` FK, backfill matched names) **without changing the claim-level model**.

### 2.5 The deliberate governed vs. free-text distinction
- **Government departments = governed.** The set is small, stable, centrally defined, and known up
  front — so it is modelled as a reference list with FK-enforced membership. This gives reliable MI.
- **Public authorities = free text.** The universe of "public authorities" is large, open-ended and
  not centrally enumerated, so up-front governance was judged premature. Free text avoids blocking
  data capture on an incomplete list, at the cost of MI quality (spelling variants, duplicates). The
  design keeps the door open to govern it later without a claim-level migration.

### 2.6 How the data flows (for completeness)
- **Bulk file → API (primary capture):** all inquest data arrives **in the uploaded bulk file** with
  the rest of the claim. `event-service` `BulkSubmissionMapper` reads the inquest fields from the
  parsed outcome and populates `ClaimInquestDataWrite` (scalars + `interested_department_codes` +
  `interested_public_authorities`) on the claim POST. (A prior iteration captured inquest data
  separately, per claim, after upload; that approach has been dropped — see ADR-0001.)
- **Feature flag `INQUESTS_ENABLED`:** inquest handling is gated. While the flag is **off**, populated
  inquest fields are **rejected at initial validation** and no inquest rows are persisted; while
  **on**, the mapping above runs and inquest rows are stored. This gate lives in the validation layer
  (ADR-0001) and does **not** change the storage model in this ADR.
- **API contract:** `claim_inquest_data_write` / `claim_inquest_data` schemas expose the flat shape;
  `POST`/`PUT`/`GET` under the claim. These now serve **corrections/edits and retrieval**, not the
  primary capture path. `PUT` (`replace`) deletes and re-inserts the child rows atomically. Department
  codes are validated against `department_reference` on write.
- **Completeness:** `InquestCompletenessDefinition` is a single config-driven policy
  (`inquest.mandatory-fields`) that treats each scalar and each repeating group (≥1 department,
  ≥1 authority) as independently mandatory-or-not. It is applied as a **validation rule** at
  initial/final validation (ADR-0001), not as a gate on a separate submission step, and is orthogonal
  to the storage model.

### 2.7 Technical observations PoC Developers want PDS to be aware of
- **Department FK identity is a live design question (see §2.8).** The PoC FKs the business `code`,
  which freezes the code forever. This is analysed as its own decision below.
- **Governed rows must be *deactivated*, not deleted.** Because `claim_interested_department`
  references governed departments, a department that has ever been selected cannot be hard-deleted
  without orphaning history. The model already carries `is_active` for exactly this. **This directly
  affects the DSIT "24 → 23" change** (see §6), and holds regardless of whether the FK is on `code`
  or the surrogate `id`.
- **`display_order UNIQUE` on public authorities** is slightly brittle: any future reordering/edit
  path must delete-then-insert (as `replace()` already does) or risk a transient unique-constraint
  clash. Not a blocker, but worth noting for the target schema.
- **No claim-type gate.** Nothing enforces that inquest rows only attach to inquest-type claims; the
  1:1 `UNIQUE` on `inquest_detail.claim_id` is the only structural guard. PDS may wish to comment on
  whether MI needs a stronger link to claim/matter type.

### 2.8 Department identity: UUID key + optional code, not code-as-key
The PoC makes the **business code** (`MOJ`, `HO`, …) the foreign key from
`claim_interested_department` to `department_reference`. That conflates two separate jobs the code
column is doing:

1. **Identity / foreign key (structural).** What every claim row points at.
2. **A human-readable handle (convenience).** A short, legible token for seeds, migrations, logs,
   test fixtures, ad-hoc SQL and the API contract.

Making the *code* do job 1 causes the core problem: because child rows reference the string, the
code can never change. A department's `display_label` may legitimately be renamed over time (machinery
-of-government changes, reorganisations), while the code — frozen forever — can **drift** until the
acronym no longer matches the name it labels (e.g. a `DSIT`-style code left describing a body that has
since merged or been renamed). Crucially, the often-cited pro — "clean, aggregatable MI" — is **not**
a differentiator here: MI is clean because a governed FK exists, and it is *equally* clean if that FK
is the surrogate `id` (you simply join and read the current `code`/`display_label`).

It is also worth being clear that UK government department codes are **not a universally recognised
cross-government primary key**. Departmental acronyms are a widely-understood *de facto* shorthand on
GOV.UK and elsewhere, but there is no authoritative shared identifier — so the code should not be
treated as one.

**Three sub-options for department identity:**

- **Option 1 — Code as FK / identity (current PoC).** Self-describing child rows; no join needed to
  read which department was selected. But the code is immutable forever → the drift risk above, and
  it leans on a token that isn't an authoritative shared key. *Not recommended.*
- **Option 2 — Surrogate `id` (UUID) as identity, `code` retained as a *mutable* attribute
  (recommended).** Child rows FK the UUID; `code` and `display_label` both become ordinary,
  changeable attributes on `department_reference`. Drift disappears — because nothing FKs the code, it
  can be corrected if a reorg makes it nonsensical. MI stays clean (join on `id`). The readable `code`
  is kept precisely *because* it is no longer frozen, so it still earns its keep in seed data,
  migrations, logs, test fixtures and as the least-bad stable token in the API contract
  (`interested_department_codes`). Low-stakes and reversible: with UUID as identity, adding or dropping
  a non-key `code` later doesn't touch claim history.
- **Option 3 — Surrogate `id` as identity, drop `code` entirely.** Leanest reference table, but seeds,
  fixtures and logs become opaque UUID literals, and the API contract must expose either UUIDs
  (opaque) or `display_label` (fragile — renames break the contract). Defensible only if no consumer
  genuinely benefits from a readable handle.

**Note — label-as-at-claim-time is orthogonal.** *None* of these options snapshots the department
label as it was when the claim was made; if a label changes, historical claims display the new label
whichever column is joined. If MI ever needs "the department as described at the time," the label (and/
or code) must be **copied onto the child row** at write time — a separate decision from identity, but
one that also cheaply restores self-describing rows under Options 2/3.

**PoC Developers' recommendation: Option 2** — surrogate UUID as the identity/FK, with `code` kept as a
mutable convenience/interchange attribute. This removes the drift risk while preserving the readability
benefits the code was really providing. Final call rests with PDS (see §8).

## 3. Decision drivers

- **PDS ownership & governance:** the model must be one PDS endorses and can steward via GLAD.
- **MI/reporting quality:** ability to aggregate, filter and trend reliably — especially over
  interested departments and (potentially) public authorities.
- **Governance of reference data:** which lists are controlled, and how they evolve (add/rename/merge)
  without breaking historical records.
- **Reference-data identity vs. attributes:** keys should be stable and free of business meaning, so
  human-readable attributes (codes, labels) can change without rewriting history or drifting out of
  sync (see §2.8).
- **Historical accuracy:** a claim's stored facts (incl. which body was selected, and its label at
  the time) must remain interpretable years later.
- **Change cost & deployment path:** whatever is chosen must go through GLAD and normal migrations,
  not remain a PoC-only schema.
- **Fitness for current data volume/shape:** inquest claims are a small subset; avoid over- or
  under-PoC Developers.

## 4. Options considered

### Option A — Adopt the current PoC model (normalised, mixed governed/free-text), with the DSIT fix
Keep the four tables as-is. Govern departments via the reference list; keep public authorities free
text; correct the DSIT entry via **deactivation** (not deletion).

- **Pros:** Already built, tested and exercised end-to-end across all three services; classic,
  well-understood relational shape; strong MI over the governed department dimension; child rows and
  audit columns give per-selection lineage; leaves a clean additive path to govern public authorities
  later; deactivation preserves historical accuracy.
- **Cons:** Public-authority MI is weak until governed (free-text variance); more tables/joins than a
  denormalised design; not yet PDS-endorsed; `display_order UNIQUE` brittleness noted above.

### Option B — Fully governed both dimensions (add `public_authority_reference` now)
Option A **plus** a governed `public_authority_reference` list and an `authority_code` FK on the
interested-authority table, replacing free text.

- **Pros:** Best possible MI on *both* interested dimensions; consistent governance story; the PoC was
  explicitly designed to allow this additively.
- **Cons:** Requires PDS to source/own/curate a canonical public-authority list — potentially large,
  open-ended and slow to agree; risks blocking data capture when a needed authority isn't yet listed
  (needs an "Other"/pending-governance escape hatch); more up-front stewardship effort. Value depends
  entirely on whether MI genuinely needs to aggregate over public authorities.

### Option C — Denormalised single table (scalars + arrays/JSONB for the repeating groups)
Collapse to one `inquest_detail` row per claim, storing interested departments/authorities as
Postgres arrays or a `JSONB` column instead of child tables.

- **Pros:** Fewest tables/joins; whole inquest record read/written in one row; flexible for evolving
  fields.
- **Cons:** Weakens referential integrity (can't FK array/JSON elements to `department_reference`
  cleanly); MI/reporting over departments becomes awkward (unnest/JSON queries, no clean joins/labels)
  — this actively undermines the primary MI driver; loses per-selection audit/lineage. Poor fit for a
  *reporting/stewardship*-owned dataset.

### Option D — Generic EAV / attribute bag
Store inquest facts as key/value attribute rows against the claim.

- **Pros:** Maximally flexible; no schema change to add fields.
- **Cons:** Anti-pattern for MI — no typing, no constraints, no governance, painful reporting.
  Rejected outright; listed only to show it was considered.

## 5. Recommendation (PoC Developers)

**Recommend Option A now, with a pre-agreed, additive path to Option B if and when PDS confirms
public-authority MI is required.**

Rationale: Option A is the best fit for the decision drivers *today*. It already delivers strong,
governed MI on the department dimension — the part that is well-defined — while the deliberate
free-text choice on public authorities avoids committing PDS to curating a large, open-ended list
before there is a proven reporting need. It is built and proven end-to-end, so the residual work is
governance/sign-off and promoting the migration through GLAD rather than new PoC Developers. Crucially,
the PoC was **designed** so that moving to Option B later is additive (add `public_authority_reference`
+ `authority_code`, backfill matched names) with **no change to the claim-level model** — so choosing
A now does not foreclose B.

Options C and D are not recommended: both trade away the referential integrity and clean joins that
make this dataset useful for MI, which runs against the primary reason PDS owns it.

Within Option A, PoC Developers further recommend the **§2.8 department-identity refinement** —
surrogate UUID as the FK with `code` retained as a mutable attribute — rather than the PoC's
code-as-FK, to remove the code/label drift risk at no MI cost.

**The one substantive thing PoC Developers asks PDS to rule on** is the governance policy for interested
**public authorities**: (a) leave free text for now (Option A), or (b) govern immediately (Option B).
Everything else in Option A is a sound default pending that call.

## 6. Consequences

- **DSIT "24 → 23":** implement as a **deactivation** (`is_active = FALSE`), **not a delete**, because
  historical claims may reference DSIT and the governed FK must remain resolvable. This holds whether
  the FK is on `code` or the surrogate `id` (see §2.8). Under the recommended UUID-identity option the
  DSIT *code* itself can additionally be corrected if the merge makes it nonsensical; under the
  current code-as-FK PoC it cannot. This keeps historical claims valid and their labels resolvable.
  The `display_order` gap left behind is cosmetic; renumbering is optional and, if done, must respect
  the `UNIQUE` constraint. PDS to confirm the intended target label/mapping (e.g. where DSIT
  responsibilities now sit) so the reference data reflects the merge correctly.
- **Reference data is governed data:** future department add/rename/merge follows the same
  deactivate-and-add discipline and goes through GLAD.
- **Public authorities:** remain free text under Option A; MI consumers must expect variance until/
  unless Option B is adopted. If Option B is later chosen, plan a backfill of matched `authority_name`
  values and an "Other/pending" handling rule.
- **Promotion out of the PoC:** the `V46` migration (and the DSIT correction) must be re-issued/owned
  as governed migrations through GLAD and the normal deployment pipeline, independent of PoC branches.
- **Minor hardening to consider for the target schema:** revisit the `display_order UNIQUE` on public
  authorities (e.g. keep insertion order without a hard unique constraint, or always delete-then-
  insert), and decide whether a claim/matter-type guard on `inquest_detail` is warranted for MI.

## 7. Definition of Done — status & follow-up tickets

| DoD item | Status |
|---|---|
| Current PoC model documented as a PDS briefing (4 tables, relationships, governed/free-text distinction) | ✅ Done — §2 of this ADR |
| Briefing presented to PDS for their input/decision | ⬜ To do — share this ADR with PDS |
| PDS's decision and rationale recorded as an ADR | ⬜ To do — PDS to complete §8 and set Status = Accepted |
| Ticket(s) raised for the schema changes (via GLAD / normal deployment) even if identical to the PoC | ⬜ To do — see below |

Suggested follow-up tickets (raise once PDS decides):
1. **GLAD migration for the agreed inquest schema** — promote the `V46` shape (as ratified) through
   GLAD/normal deployment, out of the PoC branch.
2. **DSIT reference-data correction (24 → 23)** — deactivate DSIT (and set any agreed successor
   mapping); ship as a governed migration.
3. **Department identity refinement (if PDS chooses §2.8 Option 2/3)** — re-point
   `claim_interested_department` from `department_code` to a surrogate `id` FK; retain or drop `code`
   per the decision; migrate existing rows.
4. *(Only if PDS chooses Option B)* **Introduce `public_authority_reference` + `authority_code`** and
   backfill matched `authority_name` values.
5. **Schema hardening** — `display_order` constraint review on public authorities; optional
   claim/matter-type guard on `inquest_detail`.

## 8. PDS decision (to be completed by PDS)

- **Chosen option:** _____
- **Department identity (§2.8):** code-as-FK (1) / UUID key + retain `code` (2, recommended) / UUID key, drop `code` (3) / _____
- **Snapshot department code/label onto claim rows for as-at-time MI?** yes / no / _____
- **Public-authority governance:** free text (A) / governed now (B) / _____
- **DSIT handling & successor mapping:** _____
- **Rationale:** _____
- **Decided by / date:** _____
