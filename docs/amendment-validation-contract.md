# Amendment Metadata Validation Contract (DSTEW-1594 → DSTEW-1765)

This is a **specification deliverable** for DSTEW-1594. It records the contract that the
amendment-submission endpoint (DSTEW-1765) must honour when validating the `requested_by_code` and
`amendment_reason_code` submitted against an amendment, using the governed reference data exposed by
the lookup endpoint described below.

DSTEW-1594 delivers the reference data and the lookup endpoint **only**. It does **not** implement
the validation rules below — they are owned by DSTEW-1765 and are documented here so the lookup
design is complete on its own and the amendment-endpoint story has an unambiguous contract.

## Lookup endpoint

`GET /api/v1/system/references/amendment-requested-by`

Returns the active Requested By values and, for each, the active Amendment Reason values valid for
that requesting party, in configured display order. Inactive values are excluded. The response is
stable enough to cache once at consumer startup and reuse for the lifetime of the running pod.

Example response (happy path):

```json
{
  "requested_by": [
    {
      "code": "PROVIDER",
      "display_label": "Provider",
      "display_order": 10,
      "reasons": [
        { "code": "PROVIDER_ERROR", "display_label": "Provider Error", "display_order": 10 },
        { "code": "CASE_REOPENED_REBILLED", "display_label": "Case re-opened and being billed again later", "display_order": 20 },
        { "code": "RECOVERY_FROM_CLIENT_OR_OTHER_SIDE", "display_label": "Money recovered from client and/or other side (inc. stat charge)", "display_order": 30 }
      ]
    },
    {
      "code": "CONTRACT_MANAGEMENT",
      "display_label": "Contract Management",
      "display_order": 20,
      "reasons": [
        { "code": "INCORRECT_MEANS_ASSESSMENT", "display_label": "Incorrect Means Assessment", "display_order": 10 },
        { "code": "OTHER", "display_label": "Other", "display_order": 20 }
      ]
    },
    {
      "code": "ASSURANCE",
      "display_label": "Assurance",
      "display_order": 30,
      "reasons": [
        { "code": "INCORRECT_MEANS_ASSESSMENT", "display_label": "Incorrect Means Assessment", "display_order": 10 },
        { "code": "OTHER", "display_label": "Other", "display_order": 20 }
      ]
    }
  ]
}
```

## Rules the amendment endpoint (DSTEW-1765) must honour

1. **Both fields are mandatory.** `requested_by_code` and `amendment_reason_code` must both be
   present on amendment submission.
2. **Codes, not labels.** The submitted values are the machine-readable **codes**, never the display
   labels. The endpoint validates against and persists the codes.
3. **Requested By must exist and be active.** The submitted `requested_by_code` must match a
   `requested_by_reference` row that is currently `is_active = true`.
4. **Amendment Reason must exist, be active, and be valid for the Requested By.** The submitted
   `amendment_reason_code` must match an `amendment_reason_reference` row that is
   `is_active = true` **and** whose `requested_by_code` equals the submitted `requested_by_code`.
5. **Persist codes only.** On success the endpoint persists the submitted codes against the
   amendment record. Because labels are not persisted, later relabelling in the reference data does
   not change historical amendment records.
6. **Fail safe, all-or-nothing.** Failure to validate either code does not save the amendment and
   contributes a message to the amendment endpoint's multi-message validation response.

## Validation message codes

These codes are emitted into the multi-message validation envelope owned by DSTEW-1765. Names are a
recommendation for that story to finalise; the **conditions** are the contract.

| Code | Condition |
| --- | --- |
| `MISSING_REQUESTED_BY` | `requested_by_code` absent or blank. |
| `MISSING_AMENDMENT_REASON` | `amendment_reason_code` absent or blank. |
| `UNKNOWN_REQUESTED_BY` | `requested_by_code` does not match any `requested_by_reference` row. |
| `INACTIVE_REQUESTED_BY` | `requested_by_code` matches a row that is `is_active = false`. |
| `UNKNOWN_AMENDMENT_REASON` | `amendment_reason_code` does not match any reason for the submitted Requested By. |
| `INACTIVE_AMENDMENT_REASON` | Reason matches but is `is_active = false`. |
| `INVALID_AMENDMENT_REASON_FOR_REQUESTED_BY` | Reason code exists but only under a different `requested_by_code`. |

Note: a submitted display **label** instead of a code resolves to `UNKNOWN_REQUESTED_BY` /
`UNKNOWN_AMENDMENT_REASON`, because only codes are valid input.

## Worked example — reason valid for one Requested By but not another

Submitted to the amendment endpoint:

```json
{ "requested_by_code": "ASSURANCE", "amendment_reason_code": "PROVIDER_ERROR" }
```

Expected response: `INVALID_AMENDMENT_REASON_FOR_REQUESTED_BY` collected into the multi-message
envelope. `PROVIDER_ERROR` exists in `amendment_reason_reference`, but only under
`requested_by_code = 'PROVIDER'` — not under `ASSURANCE`.

Expected persistence: none.

## Open item

Whether DSTEW-1765 validates against locally cached reference data (the consumer pod-startup caching
pattern applied to the Claims API too) or against a live lookup that can fail mid-submit is still to
be confirmed. Either way, the validation flow requires the amendment to fail safely with **no save**
if the required reference data is unavailable.

