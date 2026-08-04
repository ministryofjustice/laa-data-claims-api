# DSTEW‑1814 — Outstanding issues & AI handoff brief

**Purpose:** a self‑contained brief a future AI agent (or engineer) can act on to close the remaining
gaps on the Claim History Timeline **AMENDMENT / field‑level changes** work (DSTEW‑1814). It records
what is already done, what is deliberately deferred, and precise, evidence‑backed instructions for
each outstanding task.

**Repo:** `laa-data-claims-api` · **Module:** `claims-data/service`
**Feature:** `GET /api/v1/claims/{claimId}/history` (unified claim history read model)
**Last reviewed:** 2026‑07‑27

> Operating note for the AI: this is an implementation brief, not a licence to change scope. Treat
> the Jira ticket as the source of truth. Do not run destructive git/file commands. Validate each
> change with `get_errors` on the edited files and run the relevant test task.

---

## 0. Orientation — key files

| Concern | File |
|---|---|
| Endpoint / envelope mapping | `…/controller/ClaimHistoryController.java` |
| Service (404 on unknown claim) | `…/service/ClaimHistoryService.java` |
| Timeline SQL (UNION ALL) | `…/repository/JdbcClaimHistoryRepository.java` (`HISTORY_SQL`) |
| Row → projection | `…/mapper/ClaimHistoryEventRowMapper.java` |
| Projection DTO | `…/repository/projection/ClaimHistoryEventRow.java` |
| Amendment diff DTOs | `…/dto/amendment/AmendmentDiff.java`, `DiffEntry.java`, `ChangeSource.java` |
| Amendment entity (JSONB cols) | `…/entity/ClaimAmendment.java` (`beforeState`, `requestPayload`, `diff`) |
| Jackson config (null preservation) | `…/config/JacksonMappingConfig.java` |
| OpenAPI | `claims-data/api/open-api-specification.yml` (`claim_history_*` schemas) |
| Repo integration tests | `…/repository/JdbcClaimHistoryRepositoryIntegrationTest.java` |
| Controller integration tests | `…/controller/ClaimHistoryControllerIntegrationTest.java` |
| E2E (real PATCH → history) | `…/controller/claim/amendments/ClaimAmendmentHistoryE2eIntegrationTest.java` |
| Mapper unit tests | `…/mapper/ClaimHistoryEventRowMapperTest.java` |
| Provider Pact | `…/pactTest/…/DataClaimsApiProviderTests.java`, `util/ClaimsDataTestUtil.java` |
| Pact consumer README | `docs/claim-history-amendment-pact.md` |
| Architecture notes | `docs/claim-history-timeline-poc.md` |

**How the changes array flows:** `claim_amendment.diff` (JSONB) → `HISTORY_SQL` lifts
`diff -> 'changes'` **verbatim** → parsed to `JsonNode` in the mapper → converted to `Map` in the
controller → serialized by the primary `ObjectMapper` (no `NON_NULL`, so JSON `null` survives).

---

## 1. Status snapshot

### ✅ Done
- **`change_source` casing** normalised to upper‑case `REQUESTED` everywhere (OpenAPI enum
  `[REQUESTED, FSP]` + test literals). `ChangeSource.@JsonValue` already emitted `REQUESTED`/`FSP`.
- **NS2 — null‑vs‑missing at the HTTP boundary:** new
  `ClaimHistoryControllerIntegrationTest.preservesExplicitNullAfterAsPresentNullKey` asserts a
  cleared field surfaces as a **present‑and‑null** `after` key over real HTTP.
- **CT1 — provider Pact scaffolding:** `ClaimsDataTestUtil.getAmendmentHistoryEvent()` +
  `@State("a claim history with an amendment event exists")` in `DataClaimsApiProviderTests`.
  Documented for AaBC in `docs/claim-history-amendment-pact.md`.

### ⏸️ Deferred (decision pending, do not start without sign‑off)
- **schema_version fail‑safe (Task A)** — parked pending BA discussion. See §2.

### 🚫 Explicitly out of scope
- **Forward‑compatibility test for a 3rd `change_source`** — dropped. Product decision: the enum is
  **closed**; a new source would be a whole new integration and cannot appear spontaneously, so a
  closed contract *should* reject an unexpected value rather than pass it through.
- **Explicit‑null via the PATCH write API** — tracked in a **separate bug ticket** (see §3, Task D).

---

## 2. Task A — `schema_version` fail‑safe (DEFERRED, needs BA sign‑off)

**Problem (evidence):** `HISTORY_SQL` lifts `diff -> 'changes'` and derives `escape_case_logged`
from the v1 diff shape **without reading `diff.schema_version`**
(`JdbcClaimHistoryRepository.java`, AMENDMENT branch). Only version `1` exists today
(`AmendmentDiff.CURRENT_SCHEMA_VERSION = 1`). A future v2 with a restructured `changes` shape would
be **silently mis‑rendered** to consumers — the opposite of "fail safely".

**Decisions required before coding:**
1. **Where to gate:**
   - *Option A — SQL gate:* only emit `changes`/derive flags when
     `am.diff->>'schema_version'` is supported; else emit `[]`. One query, but the supported set is
     duplicated in SQL.
   - *Option B — Java gate (recommended):* also select `am.diff->'schema_version'` into `metadata`,
     then gate in `ClaimHistoryEventRowMapper`/controller against a single
     `AmendmentDiff.SUPPORTED_SCHEMA_VERSIONS = Set.of(1)`. Keeps policy in one Java place;
     unit‑testable without a DB.
2. **Fail‑safe response shape (recommended):** keep the event envelope +
   `requested_by_code`/`amendment_reason_code`; set `changes: []`; add `schema_version` and
   `changes_supported: false`; log a WARN; **never** 500 — one bad row must not break the timeline.
3. **Contract change:** add `schema_version` and `changes_supported` to
   `claim_history_amendment_metadata` in the OpenAPI spec.

**Implementation sketch (Option B):**
- Add `public static final Set<Integer> SUPPORTED_SCHEMA_VERSIONS = Set.of(CURRENT_SCHEMA_VERSION);`
  to `AmendmentDiff`.
- In the AMENDMENT branch of `HISTORY_SQL`, add `'schema_version', COALESCE((am.diff->>'schema_version')::int, 1)`
  to the `jsonb_build_object`.
- In the controller/mapper, when `schema_version ∉ SUPPORTED_SCHEMA_VERSIONS`: replace `changes`
  with `[]`, set `changes_supported=false`, and (optionally) null the derived flags; log WARN.
- Consider whether the SQL‑derived `escape_case_logged` should also be suppressed for unsupported
  versions (it too assumes the v1 shape).

**Tests to add:**
- Repo/mapper: unsupported `schema_version` → `changes: []` + `changes_supported: false`.
- Supported version → unchanged behaviour (regression guard).
- Controller integration: HTTP body reflects the fail‑safe shape.

**Confirm with BA:** does DSTEW‑1814 own this fail‑safe, or is it delegated to a sibling ticket
(e.g. DSTEW‑1815)? Several follow‑ups are attributed to sibling tickets in code comments.

---

## 3. Remaining tasks

### Task B — Land the AaBC consumer Pact (cross‑team, external repo)
The provider side is ready. To actually *verify* the DSTEW‑1814 contract, AaBC must publish a
consumer pact.
- Share `docs/claim-history-amendment-pact.md` with the AaBC team.
- Confirm they use the exact state string `a claim history with an amendment event exists`.
- Once their pact is in the broker, confirm provider verification passes in CI
  (`DataClaimsApiProviderTests`). If they need a different state wording or extra states
  (e.g. empty `changes`), add matching `@State` methods + fixtures here.
- **Definition of done:** a consumer pact interaction for the amendment event verifies green against
  this provider.

### Task C — (Optional) additional provider states / fixtures
If AaBC's UI needs them, add provider states + `ClaimsDataTestUtil` builders for:
- an amendment with an **empty** `changes` array;
- an amendment mixing `REQUESTED` + `FSP` with a cleared (`after: null`) field.
Only add these if a consumer interaction references them (a `@State` with no interaction is dead
code — SonarLint will flag it "never used").

### Task D — Explicit‑null through the PATCH write API (separate BUG ticket)
Currently `ClaimPatch` models fields as plain `@Nullable String`, so a JSON explicit null and an
omitted field both deserialize to `null`, and `ClaimMapper.map(String)` collapses `null →
JsonNullable.undefined()` ("no change"). Result: a provider **cannot** drive a cleared field
(before=value, after=null) end‑to‑end through PATCH; the read‑side cleared‑value case is only proven
via a hand‑seeded diff. See the `TODO(DSTEW write-side)` in
`ClaimAmendmentHistoryE2eIntegrationTest`. Fix belongs to the write‑side bug ticket (e.g. adopt
`JsonNullable` fields on `ClaimPatch` or raw‑body presence detection). When fixed, add an
end‑to‑end cleared‑field scenario to `ClaimAmendmentHistoryE2eIntegrationTest`.

---

## 4. Invariants any change MUST preserve (regression guardrails)

1. **Security:** the timeline must never expose `request_payload`, `before_state` or full snapshots.
   Guard: `JdbcClaimHistoryRepositoryIntegrationTest.amendmentMetadataOmitsRawPayloadAndBeforeState`.
2. **Null semantics:** `before`/`after` are always present keys; `null` = cleared, never omitted.
   Guards: `…IntegrationTest.preservesExplicitNullAfterAsPresentNullKey`,
   `JdbcClaimHistoryRepositoryIntegrationTest.retainsClearedClient2SurnameAsExplicitNull`,
   `ClaimHistoryEventRowMapperTest` (explicit‑null `after`). Do **not** enable `NON_NULL` on the
   primary `ObjectMapper`.
3. **`change_source` closed enum:** `REQUESTED` | `FSP`, upper‑case, verbatim from the diff.
4. **`field_identifier`** is a stable machine id (dotted/nested allowed), never a display label.
5. **`changes`** is never `null` (`COALESCE(... , '[]'::jsonb)`); empty is valid.
6. **Ordering:** `event_timestamp DESC, source_id DESC`; `event_type` never participates.
7. **404** for unknown claim id (via `ClaimHistoryService.existsById`).

---

## 5. How to validate locally

- Unit + integration test slices for this feature:
  `ClaimHistoryControllerTest`, `ClaimHistoryEventRowMapperTest`,
  `ClaimHistoryControllerIntegrationTest`, `JdbcClaimHistoryRepositoryIntegrationTest`,
  `ClaimAmendmentHistoryE2eIntegrationTest`.
- Provider Pact: `DataClaimsApiProviderTests` (requires broker access / configured consumer pact).
- After editing, run `get_errors` on changed files (ignore the repo‑wide IDE false positives:
  `@Autowired` "must be defined in valid Spring bean", SQL "cannot resolve query parameter", and
  SonarLint duplicate‑literal / "never used" warnings — these pre‑date this work).

---

## 6. Open questions (need product/BA input before related coding)

1. **schema_version (Task A):** owned by DSTEW‑1814 or a sibling ticket? Which gate (A/B) and which
   fail‑safe shape?
2. **Dependencies DSTEW‑1811 / 1813 / 1659:** their acceptance criteria are not in this repo. The
   "agreed DSTEW‑1811 call pattern" is only described narratively in
   `docs/claim-history-timeline-poc.md`. Obtain the ticket text to verify hand‑offs rather than
   inferring them.
3. **Verbatim DSTEW‑1814 ticket:** the In‑Scope / Acceptance‑Criteria / Test‑Notes / DoD bullets are
   not in the repo. Re‑confirm the matrix against the real ticket before declaring completion.

