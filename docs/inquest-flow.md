# Inquest submission validation flow

> **Capture model:** all data — inquest *and* non-inquest — arrives in a single bulk file upload and
> undergoes INITIAL validation. Valid submissions are then held in `READY_FOR_FINAL_VALIDATION` as a
> **review-before-submit** window until the provider submits (FINAL validation) or discards. There is
> **no** separate per-claim inquest data-entry step or To-Do list: the data is already present in the
> file. (A previous iteration collected inquest data per claim after upload; that sub-flow has been
> removed.)
>
> **Feature flag `INQUESTS_ENABLED`:** inquest-field handling is gated. While **disabled**, any
> populated inquest field fails INITIAL validation, so providers cannot believe the data is being
> accepted yet. While **enabled**, inquest fields are validated per the inquest validation rules.

```mermaid
flowchart TD
    A[Upload and parse\n Submission: CREATED -> READY_FOR_INITIAL_VALIDATION] --> B[INITIAL validation\n Submission: INITIAL_VALIDATION_IN_PROGRESS]
    B --> FF{"INQUESTS_ENABLED\n feature flag?"}
    FF -->|Disabled| FDIS["Inquest rule: ANY populated inquest field => Validation ERROR<br/>(inquest data is not accepted yet -<br/>providers must not think it is being captured)"]
    FF -->|Enabled| FEN["Inquest rule: validate inquest fields<br/>per the inquest INITIAL validation rules"]
    FDIS --> C
    FEN --> C
    C{"Validation ERROR?<br/>(inquest + non-inquest)"}
    C -->|Validation ERROR| D["Submission: INITIAL_VALIDATION_FAILED<br/>Claims: INVALID<br/>Provider must correct the source<br/>and create a new submission"]
    C -->|No Validation errors| E["Submission: READY_FOR_FINAL_VALIDATION<br/>Claims: READY_FOR_FINAL_VALIDATION, not yet VALID"]
    E --> G["Submission held in READY_FOR_FINAL_VALIDATION (previously DRAFT) status.<br/>All data (incl. inquest) was supplied in the uploaded file;<br/>this is a review-before-submit window, not a data-collection step."]
    G --> G1{Submission\n waiting for Provider\n action}
    G1 --> |Provider selects the\n Submit option|H1[FINAL validation]
    G1 --> |Provider selects the\n Discard option|H2["Submission and all claims set to DISCARDED"]
    G1 --> |No provider action, wait period elapsed|H3["Notification/reminder sent?\n Submission and all claims set to ABANDONED? Or Submitted?"]
    H1 --> I{"Validation ERROR?<br/>(When INQUESTS_ENABLED, inquest fields are<br/>validated per the inquest FINAL rules;<br/>when disabled, any populated inquest field is an ERROR.)"}

    I -->|Any Validation errors| J["Submission: VALIDATION_FAILED<br/>Claims: INVALID<br/>Final submission cannot return to Draft<br/>Correction requires a new submission"]

    I -->|No Validation errors| K["Submission: VALIDATION_SUCCEEDED<br/>Claims: VALID<br/>Publish SUBMISSION_VALIDATION_SUCCEEDED<br/>Notify, reporting and<br/>downstream processing"]

    %% Legend box placed next to H1 (dashed link nudges position)
    L["Duplication Rules<br/>Claims and submissions in the new status of  READY_FOR_FINAL_VALIDATION are considered when checking duplicates.
      Claims and submissions in these new statuses are not considered: DISCARDED, ABANDONED
       "]
    L -.-> H1
    style L fill:#898989,stroke:#333,stroke-width:1px
    classDef fail fill:#f8d7da,stroke:#c00,color:#000;
    classDef ok fill:#d4edda,stroke:#080,color:#000;
    class D,J,H2,H3 fail;
    class K ok;
```
