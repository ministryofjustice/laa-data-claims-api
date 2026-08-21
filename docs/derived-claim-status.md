# Derived Claim Status

## Purpose

`DerivedClaimStatus` is a **read-only, derived business status** for a claim. It presents a
single, business-friendly status to consumers (e.g. the UI) that is computed from the raw
processing status plus two boolean flags. It is exposed on the **v2 claims search response**
(`claim_response_v2.derived_claim_status`) and can be used as a **sort field**.

It is **distinct from and does not replace** the raw `claim_status`, which remains the
authoritative processing/validation status and is still returned unchanged on the response.

## Business rationale

The raw `claim_status` (`READY_TO_PROCESS`, `VALID`, `INVALID`, `VOID`) describes where a claim
sits in the validation lifecycle. Business users, however, need to distinguish between claims that
are simply valid, valid-and-amended, and valid-and-assessed, and to see voided claims as a
first-class state. `DerivedClaimStatus` folds `claim_status`, `has_assessment` and `is_amended`
into one value that reflects how the business talks about a claim, and provides a meaningful,
stable ordering for lists.

## Source fields

| Field            | Type            | Notes                                             |
|------------------|-----------------|---------------------------------------------------|
| `claim_status`   | enum            | Never null.                                       |
| `has_assessment` | boolean         | A null value is treated as `false`.               |
| `is_amended`     | boolean         | A null value is treated as `false`.               |

## Enum values

`DerivedClaimStatus` (declaration order **is** the canonical business ordering and the single
source of truth for precedence and sort ordering):

1. `ACCEPTED`
2. `AMENDED`
3. `ASSESSED`
4. `VOIDED`
5. `INVALID`
6. `READY_TO_PROCESS`

## Derivation algorithm

Rules are evaluated top-to-bottom; the **first matching rule wins**. This precedence is fixed
business logic and is **not** chronological.

1. `claim_status = VOID`              &rarr; `VOIDED`
2. `claim_status = INVALID`           &rarr; `INVALID`
3. `claim_status = READY_TO_PROCESS`  &rarr; `READY_TO_PROCESS`
4. `has_assessment = true`            &rarr; `ASSESSED`
5. `is_amended = true`                &rarr; `AMENDED`
6. otherwise (`claim_status = VALID`) &rarr; `ACCEPTED`

## Truth table

| claim_status      | has_assessment | is_amended | derived_claim_status |
|-------------------|----------------|------------|----------------------|
| VOID              | false          | false      | VOIDED               |
| VOID              | false          | true       | VOIDED               |
| VOID              | true           | false      | VOIDED               |
| VOID              | true           | true       | VOIDED               |
| INVALID           | false          | false      | INVALID              |
| INVALID           | false          | true       | INVALID              |
| INVALID           | true           | false      | INVALID              |
| INVALID           | true           | true       | INVALID              |
| READY_TO_PROCESS  | false          | false      | READY_TO_PROCESS     |
| READY_TO_PROCESS  | false          | true       | READY_TO_PROCESS     |
| READY_TO_PROCESS  | true           | false      | READY_TO_PROCESS     |
| READY_TO_PROCESS  | true           | true       | READY_TO_PROCESS     |
| VALID             | false          | false      | ACCEPTED             |
| VALID             | false          | true       | AMENDED              |
| VALID             | true           | false      | ASSESSED             |
| VALID             | true           | true       | ASSESSED             |

## Relationship to raw `claim_status`

`derived_claim_status` is **additive**. The raw `status` field on `claim_response_v2` is
unchanged, and `claim_response` (v1) is unaffected — the new field is declared on
`claim_response_v2` only. Consumers that need the processing status continue to read `status`;
consumers that want the business status read `derived_claim_status`.

## Sort behaviour

`GET /api/v2/claims?sort=derived_claim_status,asc` orders by the canonical business ordering:

- **Ascending:** `ACCEPTED, AMENDED, ASSESSED, VOIDED, INVALID, READY_TO_PROCESS`
- **Descending:** `READY_TO_PROCESS, INVALID, VOIDED, ASSESSED, AMENDED, ACCEPTED`

Sorting is performed in the backend/database across the **full paginated result set** (not in the
UI, and not just within a page). It is implemented with a SQL `CASE` expression whose ordinal
outputs are taken from `DerivedClaimStatus.ordinal()`, so the ordering is defined in exactly one
place (the enum).

### Deterministic ordering

A deterministic secondary sort by `claim.id ASC` (UUIDv7) is **always appended** as the final
ordering clause, so claims that share the same primary sort value keep a stable order across
pages. This tie-break is applied consistently to **all** v2 claims sorts:

- plain-column sorts and the unsorted default get `id ASC` appended to the `Pageable`;
- computed sorts (`total_warnings`, `submission_period`, `derived_claim_status`) append `id ASC`
  inside their ordering `Specification`.

### Error handling

Unsupported sort keys continue to return the endpoint's existing **400 Problem Details** response
(`ClaimBadRequestException` &rarr; `DataClaimsExceptionHandler`). Adding `derived_claim_status`
simply makes it a recognised key.

## Multi-field sort caveat

The computed sort fields (`total_warnings`, `submission_period`, `derived_claim_status`) are
implemented via JPA `query.orderBy(...)`, which **replaces** the query's entire order list. As a
result:

- **Plain columns** may be combined freely (e.g. `sort=status,asc&sort=fee_code,desc`); each is
  applied in order and the `id` tie-break is appended.
- **Computed fields behave as single-field sorts** and must **not** be combined with any other
  sort field in the same request. If a computed field is combined with another field, the ordering
  Spring Data derives from the remaining `Pageable` sort would override the computed
  `Specification`'s `orderBy`, so the computed ordering is not guaranteed to be applied. Only the
  first matching computed field is honoured if more than one is supplied.

This matches the pre-existing behaviour of `total_warnings` and `submission_period`; the derived
status sort intentionally follows the same, light-touch pattern.

### How full computed multi-field sort could be added later

If combining computed fields with other fields becomes a requirement, the ordering should be
unified into a **single** builder rather than several independent `Specification`s that each call
`query.orderBy(...)`:

1. Translate every requested sort order into a JPA `Order`:
   - plain columns &rarr; `root`/join paths (as today via `ClaimSortField`),
   - `total_warnings` &rarr; the warning-count subquery expression,
   - `submission_period` &rarr; the `to_date(...)` expression,
   - `derived_claim_status` &rarr; the `CASE` ordinal expression.
2. Collect them into one ordered `List<Order>`, append `cb.asc(root.get("id"))` as the final
   clause, and issue a single `query.orderBy(orders)`.
3. Pass an **unsorted** `Pageable` (page/size only) to `findAll` so Spring Data never re-applies or
   overrides the ordering.

This removes the "last `orderBy` wins" limitation at the cost of a larger change to
`ClaimService`/`ClaimSpecification`, and would need regression tests for every existing sort path.

## Implementation notes

- **Single source of truth for derivation:**
  `util.DerivedClaimStatusResolver#resolve(ClaimStatus, boolean, boolean)` is the only Java
  implementation of the precedence rules. Response mapping (`ClaimMapper.toClaimResponseV2`)
  consumes it directly.
- **Single source of truth for ordering:** the `DerivedClaimStatus` enum declaration order. The
  SQL `CASE` in `ClaimSpecification.orderByDerivedClaimStatus` uses `DerivedClaimStatus.ordinal()`
  for its outputs; no separate precedence list, map or constant is defined anywhere else.
- **Parity:** the SQL `CASE` precedence mirrors the resolver. An integration/parity test seeds a
  claim for every derivation combination and asserts the DB sort order equals the resolver order,
  so the two encodings cannot silently diverge.
- **No schema change:** `derived_claim_status` is not persisted; it is computed on read for both
  mapping and sorting.

## Test expectations

- **Unit** — `DerivedClaimStatusResolver`: full 16-row truth table, `null` booleans treated as
  `false`, `null` status rejected.
- **Unit** — `ClaimMapper.toClaimResponseV2`: `derived_claim_status` is populated and the raw
  `status` is unchanged.
- **Unit** — `ClaimService`: `derived_claim_status` is stripped from the `Pageable` before query
  execution; unsupported sort keys raise `ClaimBadRequestException`; the `id` tie-break is applied
  for plain/unsorted requests but not layered on top of computed sorts.
- **Integration** — `GET /api/v2/claims`:
  - `sort=derived_claim_status,asc` and `,desc` return the full canonical ordering across pages.
  - Claims sharing a derived status keep a stable `id ASC` order across page boundaries.
  - The response contains `derived_claim_status`; v1 responses are unchanged.
  - An unsupported sort key returns 400 Problem Details.
- **Parity** — DB `CASE` ordering equals `DerivedClaimStatusResolver` for every combination, and
  the `CASE` ordinals match `DerivedClaimStatus.ordinal()`.

