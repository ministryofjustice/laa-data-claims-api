# Claim History Timeline — Read-Model POC

A proof-of-concept read model that returns the **complete history of a single claim** as one
unified, chronologically ordered timeline, loaded with a **single** PostgreSQL query.

This is a **query/read use case**. It deliberately avoids JPA entities, entity graphs and lazy
loading. It uses `JdbcClient`, native SQL, `UNION ALL`, server-side `jsonb_build_object`, and a flat
projection.

---

## 1. Deliverables map

| # | Deliverable | Location |
|---|-------------|----------|
| 1 | Repository interface | `repository/ClaimHistoryRepository.java` |
| 2 | SQL query | `repository/JdbcClaimHistoryRepository.java` (`HISTORY_SQL`) + [below](#2-sql-query) |
| 3 | Projection / DTO | `repository/projection/ClaimHistoryEventRow.java` |
| 4 | Repository implementation | `repository/JdbcClaimHistoryRepository.java` |
| 5 | Row mapper | `repository/mapper/ClaimHistoryEventRowMapper.java` |
| 6 | Example service usage | `service/ClaimHistoryService.java` |
| 7 | Example output | [§7](#7-example-output) |
| 8 | Index recommendations | [§8](#8-index-recommendations) |
| 9 | Performance characteristics | [§9](#9-performance-characteristics) |
| 10 | Why `UNION ALL` | [§10](#10-why-union-all-over-union) |

---

## Data model notes (as verified against migrations)

- All tables live in the `claims` schema (`spring.jpa...default_schema: claims`); the SQL qualifies
  every table with `claims.` so it is independent of the connection `search_path`.
- The SUBMISSION event enriches its metadata from the parent `claims.submission` row (joined via
  `claim.submission_id`), exposing `submission_period`, `office_account_number` and `area_of_law`.
- `created_by_user_id` is `NOT NULL` on all three source tables today, but the query still applies
  `COALESCE(..., 'SYSTEM')` so the `actorId` contract holds even if that ever changes or if future
  sources are system-generated.

---

## 2. SQL query

```sql
SELECT event_type, event_timestamp, actor_id, source_id, metadata
FROM (
    -- ---------- SUBMISSION (source: claim) ----------
    SELECT
        'SUBMISSION'                             AS event_type,
        c.created_on                             AS event_timestamp,
        COALESCE(c.created_by_user_id, 'SYSTEM') AS actor_id,
        c.id                                     AS source_id,
        jsonb_build_object(
            'submission_period',     s.submission_period,
            'office_account_number', s.office_account_number,
            'area_of_law',           s.area_of_law
        )                                        AS metadata
    FROM claims.claim c
    JOIN claims.submission s ON s.id = c.submission_id
    WHERE c.id = :claimId

    UNION ALL

    -- ---------- AMENDMENT (source: claim_amendment) ----------
    SELECT
        'AMENDMENT'                              AS event_type,
        am.created_on                            AS event_timestamp,
        COALESCE(am.created_by_user_id, 'SYSTEM') AS actor_id,
        am.id                                    AS source_id,
        jsonb_build_object(
            'requested_by_code',     am.requested_by_code,
            'amendment_reason_code', am.amendment_reason_code,
            'pricing_recalculated',  (cfd.id IS NOT NULL),
            'price_changed',         COALESCE(cfd.is_price_changed, false),
            'escape_case_logged',    COALESCE(cfd.escape_case_flag, false),
            'changes',               COALESCE(am.diff -> 'changes', '[]'::jsonb)
        )                                        AS metadata
    FROM claims.claim_amendment am
    LEFT JOIN claims.calculated_fee_detail cfd ON cfd.claim_amendment_id = am.id
    WHERE am.claim_id = :claimId

    UNION ALL

    -- ---------- ASSESSMENT (source: assessment) ----------
    SELECT
        CASE WHEN asmt.assessment_type = 'VOID' THEN 'VOID' ELSE 'ASSESSMENT' END AS event_type,
        asmt.created_on                          AS event_timestamp,
        COALESCE(asmt.created_by_user_id, 'SYSTEM') AS actor_id,
        asmt.id                                  AS source_id,
        CASE
            WHEN asmt.assessment_type = 'VOID' THEN
                jsonb_build_object(
                    'assessment_type',   asmt.assessment_type,
                    'assessment_reason', asmt.assessment_reason
                )
            ELSE
                jsonb_build_object(
                    'assessment_type',    asmt.assessment_type,
                    'assessment_outcome', asmt.assessment_outcome,
                    'assessment_reason',  asmt.assessment_reason
                )
        END                                      AS metadata
    FROM claims.assessment asmt
    WHERE asmt.claim_id = :claimId
) AS claim_history
ORDER BY event_timestamp DESC, source_id DESC
LIMIT :limit;
```

Key properties:

- **Ordering is purely chronological**: `event_timestamp DESC`, with the UUIDv7 `source_id DESC` as a
  deterministic tie-breaker. `event_type` never participates in ordering.
- A **void** assessment (`assessment_type = 'VOID'`) is surfaced as a distinct `VOID` event whose
  metadata contains only `assessment_type` and `assessment_reason`; all other assessments become
  `ASSESSMENT` events carrying `assessment_type`, `assessment_outcome` and `assessment_reason`.
- Amendment events expose the requester and reason, pricing/escape flags derived from the linked
  `calculated_fee_detail`, and the field-level `changes` array lifted from the amendment `diff`
  document (`diff -> 'changes'`) as nested jsonb (not stringified).
- Only `LIMIT` is used — **no `OFFSET`**.

---

## 3–6. Java components

- **Projection** — `ClaimHistoryEventRow(String eventType, Instant eventTimestamp, String actorId,
  UUID sourceId, JsonNode metadata)`. Uses Jackson 2 `com.fasterxml.jackson.databind.JsonNode` to
  match the existing codebase.
- **Interface** — `ClaimHistoryRepository.findHistory(UUID claimId, int limit)`.
- **Implementation** — `JdbcClaimHistoryRepository` executes the single query above via `JdbcClient`.
- **Row mapper** — `ClaimHistoryEventRowMapper` reads `timestamptz` → `Instant`, and parses the
  server-built jsonb `metadata` text into a `JsonNode`.
- **Service** — `ClaimHistoryService.getTimeline(claimId[, pageSize])` for example usage.

Example call:

```java
List<ClaimHistoryEventRow> timeline = claimHistoryService.getTimeline(claimId, 50);
```

---

## 7. Example output

Given:

```
10:00 Claim Submitted
10:05 Assessment Created
10:10 Claim Amended
10:15 Assessment Recalculated
```

`findHistory(claimId, 50)` returns (newest first):

```
eventType    eventTimestamp        actorId   sourceId (UUIDv7)   metadata
-----------  --------------------  --------  ------------------  ------------------------------------------
ASSESSMENT   2026-07-09T10:15:00Z  u-8891    018f...f4           { "assessment_type": "ESCAPE_CASE_ASSESSMENT",
                                                                   "assessment_outcome": "REDUCED_TO_FIXED_FEE",
                                                                   "assessment_reason": "Escape Fee Case Assessment" }
AMENDMENT    2026-07-09T10:10:00Z  u-4410    018f...c2           { "requested_by_code": "PROVIDER",
                                                                   "amendment_reason_code": "PROVIDER_ERROR",
                                                                   "pricing_recalculated": true,
                                                                   "price_changed": false,
                                                                   "escape_case_logged": false,
                                                                   "changes": [ { "field_identifier": "net_profit_costs_amount",
                                                                                  "before": "100.00", "after": "120.00",
                                                                                  "change_source": "REQUESTED" } ] }
ASSESSMENT   2026-07-09T10:05:00Z  SYSTEM    018f...a7           { "assessment_type": "ESCAPE_CASE_ASSESSMENT",
                                                                   "assessment_outcome": "PAID_IN_FULL",
                                                                   "assessment_reason": "Escape Fee Case Assessment" }
SUBMISSION   2026-07-09T10:00:00Z  u-4410    018f...91           { "submission_period": "APR-2026",
                                                                   "office_account_number": "0X123Y",
                                                                   "area_of_law": "CRIME LOWER" }
```

The 10:05 assessment shows `actorId = "SYSTEM"` to illustrate the fallback (in the current schema
the column is `NOT NULL`, so this demonstrates the contract rather than typical data).

---

## 8. Index recommendations

Each branch filters by `claim_id` (submission by `claim.id` PK) and the whole timeline sorts by
`created_on DESC, id DESC`. A composite index per source lets PostgreSQL satisfy both the filter and
the sort from a single index range scan, and — critically — makes future keyset pagination fast.

Current state:

| Table | Existing relevant index |
|-------|-------------------------|
| `claims.claim` | PK on `id` (submission lookup is a PK probe — already optimal) |
| `claims.claim_amendment` | `(claim_id)`, `(created_on DESC)` — not composite |
| `claims.assessment` | `(claim_id)` only |
| `claims.calculated_fee_detail` | `(claim_id)` only (for the `totalValue` sub-select) |

Recommended additions:

```sql
CREATE INDEX ix_claim_amendment_claim_created_id
    ON claims.claim_amendment (claim_id, created_on DESC, id DESC);

CREATE INDEX ix_assessment_claim_created_id
    ON claims.assessment (claim_id, created_on DESC, id DESC);

-- Supports the "latest fee" sub-select feeding totalValue.
CREATE INDEX ix_calculated_fee_detail_claim_created_id
    ON claims.calculated_fee_detail (claim_id, created_on DESC, id DESC);
```

Notes:
- These make the existing single-column `(claim_id)` indexes on `claim_amendment` and `assessment`
  redundant as leading-column duplicates; they can be dropped after validation to save write cost.
- The `(claim_id, created_on DESC, id DESC)` column order exactly matches both the `WHERE` predicate
  and the `ORDER BY`, so the planner can return already-sorted rows and stop early once `LIMIT` /
  the cursor bound is satisfied.

---

## 9. Performance characteristics

**Expected plan.** For one claim the planner produces an `Append` (the `UNION ALL`) over three
branches:

- **SUBMISSION** — `Index Scan` / `Index Only` probe on `claim_pkey` (`c.id = :claimId`): exactly one
  row. The `totalValue` correlated sub-select is a bounded `Index Scan … LIMIT 1` on
  `calculated_fee_detail`.
- **AMENDMENT** — `Index Scan` on `ix_claim_amendment_claim_created_id`, range = one claim, rows
  pre-sorted by `created_on DESC, id DESC`.
- **ASSESSMENT** — `Index Scan` on `ix_assessment_claim_created_id`, same shape.

The outer `ORDER BY event_timestamp DESC, source_id DESC` merges the three already-sorted streams;
because each branch is small (typically 20–50 rows total per claim) the top-N sort is trivial and
`LIMIT` bounds the result. All predicates are equality/`LIMIT`, so cost scales with the number of
events for the **single** claim, not table size.

**Why it stays fast at scale.**
- No cross-claim scans: every branch is anchored on `claim_id` / PK.
- No `OFFSET`: cost never grows with page depth (see keyset note below).
- No entity hydration, no N+1, no lazy-load round-trips: one network round-trip, flat rows.
- JSON is assembled server-side (`jsonb_build_object`), so no per-row Java mapping of dozens of
  columns — the mapper parses one JSON document per row.

**Potential bottlenecks / watch-items.**
- **Missing composite indexes** (see §8) → the planner falls back to `(claim_id)` scan + explicit
  `Sort`. Fine at 20–50 rows, but it defeats early-stop once volumes/pagination grow.
- **Large jsonb payloads** (amendment `diff -> 'changes'`) dominate row width and network
  transfer; if timelines get large, consider projecting a summarised `changes` set and lazy-loading
  the full document on demand.
- **`ORDER BY` across the `Append`** cannot use a single index (rows come from three tables), so the
  final merge/sort is unavoidable — but it operates only on the current claim's (small) event set.
- The `totalValue` sub-select runs once (single SUBMISSION row), so it is not a hot path.

**Keyset (cursor) pagination — already designed for.** No structural change is needed. Add the bound
to each branch so every index scan is still used:

```sql
--   ... WHERE claim_id = :claimId
AND (created_on, id) < (:cursorTimestamp, :cursorSourceId)
--   ... ORDER BY created_on DESC, id DESC   (unchanged)
--   LIMIT :pageSize
```

The `(event_timestamp, source_id)` returned in each row is exactly the cursor. The implementation
exposes `JdbcClaimHistoryRepository.CURSOR_PREDICATE` as the seed for this follow-up.

---

## 10. Why `UNION ALL` over `UNION`

- **Correctness.** `UNION` de-duplicates rows. Timeline events from different tables are distinct
  business facts — two events could legitimately share a timestamp/actor and must **both** appear.
  De-duplication would silently drop real history.
- **Performance.** `UNION` forces a distinct/sort (hash or sort to remove duplicates) across the
  merged set before the outer `ORDER BY`; `UNION ALL` simply appends the branches (`Append` node),
  leaving a single, cheap top-N sort. It avoids an extra full-set materialisation and comparison.
- **Predictability.** With UUIDv7 primary keys as `source_id`, rows are already globally unique, so
  `UNION`'s de-duplication can never remove anything — it is pure overhead.
```

