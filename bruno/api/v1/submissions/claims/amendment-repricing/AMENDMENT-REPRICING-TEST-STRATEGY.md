# Amendment Repricing Bruno Test Strategy (DSTEW-1757..1762)

This strategy adds a runnable Bruno scenario pack for synchronous amendment repricing and maps each scenario to the Jira parent-story acceptance criteria.

## Scope

- Trigger behavior (pricing vs non-pricing)
- Request path `PATCH /api/v1/submissions/:submission_id/claims/:claim_id`
- FSP success, validation failure, technical failure/timeout outcomes
- No-save verification after failed amendment paths
- Missing baseline calculated-fee-detail rejection behavior(the claim can’t be repriced because there’s no existing calculated fee detail to compare against.)

## Prerequisites

1. Open `bruno/` collection in Bruno UI.
2. Select target environment (`LOCAL`, `FEATURE`, `UAT`).
3. Set `HOST` and `AUTH_TOKEN` in Bruno UI environment editor.
4. Run setup requests:
   - `SETUP: Grab valid submission id`
   - `SETUP: Grab claim id from submission`
   - `Make Claim Valid`
   - `SETUP: Verify claim has fee calculation`

## Additional runtime variables

Set these in Bruno UI only when needed:

- `CLAIM_ID_NO_CFD` - claim ID that has no baseline calculated fee detail. If unset, scenario `09` becomes a safe no-op.
- `AR_STUBBED_FSP` - set to `"true"` when running against an environment where FSP responses are controllable (MockServer/stubs). If unset, scenarios `06`, `07`, `08` execute the PATCH request but skip strict assertions so the whole folder can be run cleanly against real UAT/LOCAL.

Set automatically by scenario scripts:

- `AR_CLAIM_VERSION` - claim version used by amendment PATCH requests. Refreshed by a `script:pre-request` on every amendment PATCH via a live `GET` of the claim.
- `AR_NEXT_NET_PROFIT` - dynamic pricing amount (current + delta) so re-runs always produce a diff and a new fee snapshot.
- `AR_PRE_S04_CFD_ID` - CFD id snapshot taken immediately before scenario `04` for the `05` assertion.
- `AR_BASELINE_CFD_ID`, `AR_BASELINE_TOTAL_AMOUNT`
- `AR_POST_PRICING_CFD_ID`, `AR_POST_PRICING_TOTAL_AMOUNT`
- `CLAIM_ID_NO_CFD_VERSION` - fetched by scenario `09` pre-request when `CLAIM_ID_NO_CFD` is set.

## Auto claim-version handling (pre-request scripts)

Every amendment PATCH scenario (`02`, `04`, `06`, `07`, `09`) has a `script:pre-request` block that:

1. Reads the current claim via `GET /api/v1/submissions/{SUBMISSION_ID}/claims/{CLAIM_ID}` (or `CLAIM_ID_NO_CFD` for `09`).
2. Writes `AR_CLAIM_VERSION` (or `CLAIM_ID_NO_CFD_VERSION`) into env vars for the PATCH body.
3. Scenario `04` additionally derives `AR_NEXT_NET_PROFIT` guaranteed to differ from the current claim's `net_profit_costs_amount`, so the amendment always triggers repricing.

This removes the need to hand-refresh `AR_CLAIM_VERSION` between runs and prevents stale-version `CLAIM_VERSION_CONFLICT` errors.

## Verified expected outcome (LOCAL / UAT, no FSP stubs)

```
01 Capture baseline claim                                   200
02 Non-pricing amendment (expect 204)                       204
03 Verify non-pricing skips repricing                       200
04 Pricing amendment success (expect 204)                   204
05 Verify pricing creates new fee snapshot                  200
06 Pricing amendment validation failure (expect 400)        204  (assertions skipped)
07 Pricing amendment technical failure (expect 503)         4xx  (assertions skipped)
08 Verify no-save after failed amendment                    200  (assertions skipped)
09 Missing baseline CFD rejection (expect 400)              200/400 (skipped unless CLAIM_ID_NO_CFD set)
```

To enforce strict assertions on `06`/`07`/`08`, set `AR_STUBBED_FSP=true` in the environment and point the API at a stubbed FSP.

## Scenario pack

Folder: `bruno/api/v1/submissions/claims/amendment-repricing/`

Run in sequence:

1. `01 Capture baseline claim`
2. `02 Non-pricing amendment (expect 204)`
3. `03 Verify non-pricing skips repricing`
4. `04 Pricing amendment success (expect 204)`
5. `05 Verify pricing creates new fee snapshot`
6. `06 Pricing amendment validation failure (expect 400)`
7. `07 Pricing amendment technical failure (expect 503)`
8. `08 Verify no-save after failed amendment`
9. `09 Missing baseline CFD rejection (expect 400)`

## Notes on failure scenarios

- Requests `06` and `07` are designed for environments where FSP behavior can be controlled (e.g., MockServer/stubbed downstream).
- In non-stubbed environments, those requests may not deterministically return the expected status.
- `08` verifies that failed amendment scenarios do not modify the latest calculated fee snapshot.

## UAT working example (deterministic path)

Use this sequence in `UAT` for a reliable end-to-end run without stubbed downstream controls:

1. `SETUP: Grab valid submission id`
2. `SETUP: Grab claim id from submission`
3. `Make Claim Valid`
4. `SETUP: Verify claim has fee calculation`
5. `01 Capture baseline claim`
6. `02 Non-pricing amendment (expect 204)`
7. `03 Verify non-pricing skips repricing`
8. `04 Pricing amendment success (expect 204)`
9. `05 Verify pricing creates new fee snapshot`

Expected UAT outcomes for this path:

- `02` returns `204` and keeps baseline CFD + total unchanged (`03`).
- `04` returns `204`.
- `05` returns `200` and has a different `calculated_fee_detail_id` than baseline.

Run `06` and `07` only in environments where FSP error responses are controllable.

## Acceptance-criteria mapping

- Non-pricing skip: scenarios `02` + `03`
- Pricing request from post-amendment state + success handoff: scenarios `04` + `05`
- Validation failure no-save: scenarios `06` + `08`
- Technical/timeout no-save: scenarios `07` + `08`
- Missing baseline CFD controlled rejection: scenario `09`




