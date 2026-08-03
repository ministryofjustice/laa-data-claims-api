# Amendment Repricing Bruno Test Strategy (DSTEW-1757..1762)

This strategy adds a runnable Bruno scenario pack for synchronous amendment repricing and maps each scenario to the Jira parent-story acceptance criteria.

## Scope

- Trigger behavior (pricing vs non-pricing)
- Request path `PATCH /api/v1/submissions/:submission_id/claims/:claim_id`
- FSP success, validation failure, technical failure/timeout outcomes
- No-save verification after failed amendment paths
- Missing baseline calculated-fee-detail rejection behavior

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

- `CLAIM_ID_NO_CFD` - claim ID that has no baseline calculated fee detail.

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

## Acceptance-criteria mapping

- Non-pricing skip: scenarios `02` + `03`
- Pricing request from post-amendment state + success handoff: scenarios `04` + `05`
- Validation failure no-save: scenarios `06` + `08`
- Technical/timeout no-save: scenarios `07` + `08`
- Missing baseline CFD controlled rejection: scenario `09`


