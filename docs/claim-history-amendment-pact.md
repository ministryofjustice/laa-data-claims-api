# Claim History — AMENDMENT event Pact (provider side)

Provider‑side contract notes for the **AMENDMENT** event on the claim history timeline
(`GET /api/v1/claims/{claimId}/history`), covering the DSTEW‑1814 field‑level `changes` surface.

This repo (`laa-data-claims-api`) is the **provider**. The consumer (**AaBC**) lives in an external
repo, so the consumer contract must be authored there. This document tells the AaBC team exactly
which provider state to name and which response shape to expect, so their published pact verifies
against our provider without back‑and‑forth.

---

## 1. How provider verification works here

- `DataClaimsApiProviderTests` (`@Provider("laa-data-claims-api")`, `@PactBroker`) pulls **all** pacts
  published against this provider from the Pact Broker and replays each interaction.
- Every interaction declares a **provider state** (a string). Our test class has a matching
  `@State("…")` method that stubs the repositories so the endpoint returns deterministic data.
- **A provider `@State` does nothing until a consumer publishes an interaction that names it.**
  We have set up the state; AaBC must publish the matching interaction.

---

## 2. The provider state to target

| | |
|---|---|
| **Provider name** | `laa-data-claims-api` |
| **State string (verbatim)** | `a claim history with an amendment event exists` |
| **Request** | `GET /api/v1/claims/{claimId}/history` |
| **Handler** | `DataClaimsApiProviderTests.aClaimHistoryWithAnAmendmentEventExists()` |
| **Fixture builder** | `ClaimsDataTestUtil.getAmendmentHistoryEvent()` |

The handler returns an AMENDMENT event (newest) followed by a SUBMISSION event (oldest), so the
consumer can also assert reverse‑chronological ordering if it wishes.

> ⚠️ The state string must match **character for character**. If AaBC needs a different wording,
> tell us and we will add/rename the `@State` method — do not silently diverge.

---

## 3. Response shape the consumer should pin

The AMENDMENT event carries its event‑specific attributes in the open `metadata` container. The
field‑level changes are lifted verbatim from the persisted amendment `diff`.

```jsonc
{
  "claim_id": "<uuid>",
  "events": [
    {
      "event_type": "AMENDMENT",
      "event_timestamp": "<ISO-8601 date-time>",
      "actor_id": "<string>",            // "SYSTEM" when the source row has no user id
      "source_id": "<uuid>",             // UUIDv7 of the claim_amendment row
      "metadata": {
        "requested_by_code": "PROVIDER",
        "amendment_reason_code": "PROVIDER_ERROR",
        "pricing_recalculated": true,
        "price_changed": true,
        "escape_case_logged": false,
        "changes": [
          {
            "field_identifier": "client.clientForename",
            "before": "Alice",
            "after": "Alexandra",
            "change_source": "REQUESTED"
          },
          {
            "field_identifier": "fee.totalAmount",
            "before": "100.00",
            "after": "180.00",
            "change_source": "FSP"
          },
          {
            "field_identifier": "fee.schemeId",
            "before": null,                 // explicit JSON null — present, NOT omitted
            "after": "SCHEME-TEST",
            "change_source": "FSP"
          }
        ]
      }
    },
    { "event_type": "SUBMISSION", "…": "…" }
  ]
}
```

### Contract points AaBC should assert
- **`change_source` is a closed enum: `REQUESTED` | `FSP`** (upper‑case, verbatim from the diff).
  A third value is out of scope by design — a new source would be a whole new integration, not a
  spontaneous value. Do **not** pin only a type match; pin the allowed values.
- **`field_identifier`** is a stable machine identifier (may be dotted/nested, e.g. `fee.schemeId`),
  never a display label.
- **`before` / `after`** are the field's natural JSON type. **`null` means the field was cleared /
  not previously set** and is emitted as a **present key with a null value** — it is *not* the same
  as the key being absent. Consumers must treat `{"before": null}` and a missing `before`
  differently. (Provider guards this with
  `ClaimHistoryControllerIntegrationTest.preservesExplicitNullAfterAsPresentNullKey`.)
- **`changes` may be empty** (`[]`) for an amendment with no field‑level diff; it is never `null`.
- The amendment metadata **never** exposes `request_payload`, `before_state` or full snapshots.

### Fields AaBC should NOT over‑constrain
- `metadata` is deliberately open (`additionalProperties: true`); pin the keys you consume, not the
  whole object, so later event types/attributes don't break your pact.
- `pricing_recalculated` / `price_changed` / `escape_case_logged` are amendment‑derived booleans;
  pin them only if your UI depends on them.

---

## 4. Reference schemas

See `claims-data/api/open-api-specification.yml`:
- `claim_history_event` — the envelope.
- `claim_history_amendment_metadata` — AMENDMENT `metadata`.
- `claim_history_change_entry` — a single `changes[]` entry (`field_identifier`, `change_source`
  enum `[REQUESTED, FSP]`, nullable `before`/`after`).

---

## 5. Checklist for the AaBC (consumer) side

- [ ] Publish a consumer pact for `GET /api/v1/claims/{claimId}/history`.
- [ ] Use the provider state string **`a claim history with an amendment event exists`**.
- [ ] Assert `event_type = "AMENDMENT"`, the `metadata` keys you consume, and the `changes[]` shape.
- [ ] Pin `change_source` to the closed set `["REQUESTED", "FSP"]`.
- [ ] Include at least one `changes[]` entry with `before: null` to lock the present‑null semantics.
- [ ] Publish under your branch; provider verification runs against the tag configured in the broker
      (`PACT_CONSUMER_NAME` / `PACT_CONSUMER_BRANCH` can override during local runs).

