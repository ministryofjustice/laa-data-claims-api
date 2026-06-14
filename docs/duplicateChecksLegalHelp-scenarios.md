# `duplicateChecksLegalHelp.feature` scenario guide

Source feature:
`claims-data/service/src/bddTest/resources/features/bdd/duplicateChecksLegalHelp.feature`

## What this feature is for

This feature is the **API-focused port** of the Legal Help duplicate-check scenarios originally written in the UI test suite. It verifies what can be proven **directly from `laa-data-claims-api`** without depending on the downstream event service.

At a high level, these scenarios check that Legal Help bulk submissions are **accepted** when the submitted claims are not duplicates under the rules observable from this API.

## Scope of the API feature

The scenarios drive the real HTTP API and assert API-visible behaviour only:

- `POST /api/v1/bulk-submissions`
  - uploads a CSV/TXT/XML Legal Help bulk file
- `GET /api/v1/bulk-submissions/{id}`
  - confirms the bulk submission record was persisted
  - confirms the schedule area of law is `LEGAL HELP`
- `GET /api/v1/bulk-submissions/{id}/summary`
  - polled briefly when a scenario waits for parsing/import progress
- `PATCH /api/v1/bulk-submissions/{id}`
  - used in one scenario to force a prior submission into `VALIDATION_FAILED`

## What it does **not** test

This feature explicitly avoids checks that require `laa-data-claims-event-service` or later asynchronous processing, including:

- claim-level duplicate errors produced after downstream parsing/validation
- within-file duplicate validation messages
- submission-period “already exists” error banners
- claim voiding flows

Those remain covered by the cross-service end-to-end suite in `bulk-submission-and-fee-scheme-tests-`.

## Test mechanics used by the feature

### Background
Every scenario starts from:

- `Given the Legal Help duplicate checks BDD scaffold is ready`

This is just a named anchor step. It does not perform work itself; it provides a clear baseline equivalent to the old UI feature’s starting state.

### File generation
Most scenarios use an in-process generator that creates fresh Legal Help files in:

- `csv`
- `txt`
- `xml`

The generator:

- builds valid Legal Help `OFFICE`, `SCHEDULE`, and `OUTCOME` lines
- applies claim overrides from the scenario table (`ucn`, `ufn`, `feeCode`, `office`, etc.)
- allocates a fresh submission period per office/area-of-law to avoid accidental clashes

### Legacy UI wording carried into API tests

Some steps keep the original UI-oriented phrasing for traceability:

- `click import` is a **no-op** in the API suite
- the actual import action already happened when the file was uploaded with `POST /api/v1/bulk-submissions`

This means the scenarios are best read as API workflow descriptions rather than literal browser actions.

### Key assertions used repeatedly
Depending on the scenario, the feature asserts that:

- upload returns **HTTP `201`**
- a **bulk submission id** is returned
- the response contains expected submission ids
- the persisted bulk submission exists
- the persisted schedule area of law is **Legal Help**
- the generated file contains the expected number of `OUTCOME` records
- two accepted uploads produce **different** bulk submission ids

## Scenario inventory

There are **11 scenario definitions** in the file:

- **2 concrete scenarios**
- **9 scenario outlines**, each executed for `csv`, `txt`, and `xml`

That gives **29 concrete example runs** in total.

---

## Scenario-by-scenario details

### 1. `First occurrence is accepted via API upload (static fixture)`
**Purpose**
- Smoke test of the multipart upload pipeline using a fixed fixture file.

**What it does**
- Uploads `test_upload_files/csv/outcomes.csv` for office `0U099L`.

**What it proves**
- the bulk upload endpoint accepts a valid Legal Help file
- the API returns `201`
- a bulk submission id is created
- the upload response includes exactly **1 submission id**

**Why it matters**
- Establishes a simple baseline that the HTTP upload contract works before testing generated duplicate-check combinations.

---

### 2. `Same legal-help file can be submitted twice via the API`
**Purpose**
- Confirms the API itself accepts two uploads of the same file and records them as separate bulk upload requests.

**What it does**
- Uploads the same static fixture twice for the same office.

**What it proves**
- both uploads return `201`
- the scenario records **2 accepted bulk uploads**
- each upload gets a **unique bulk submission id**

**Important nuance**
- This does **not** prove that the claims are non-duplicates.
- It proves only that `laa-data-claims-api` accepts the POST requests and creates separate bulk submission records. Any later duplicate verdict belongs to downstream processing and is outside this feature’s scope.

---

### 3. `First occurrence is accepted (generated <format>)`
Formats: `csv`, `txt`, `xml`

**Purpose**
- Verifies a first-time generated Legal Help file is accepted in every supported format.

**What it does**
- Generates a file with **2 claims**, both using fee code `CAPA`.
- Because only `feeCode` is overridden, the generator still gives each row its own generated identifiers and dates.
- Uploads it and waits briefly for import progress.

**What it proves**
- the upload succeeds with `201`
- the persisted bulk submission is for **Legal Help**
- the generated file contains **2 outcome rows**, matching the scenario expectation

**Duplicate-rule meaning**
- This is mainly a baseline acceptance test for a newly generated Legal Help file in all supported formats.
- It is **not** asserting downstream duplicate detection between two identical claim rows.

---

### 4. `Should have no errors when UFN differs - single submission (<format>)`
Formats: `csv`, `txt`, `xml`

**Purpose**
- Verifies two claims in the **same file** are treated as distinct when their **UFN** differs.

**Data pattern**
- same `UCN`
- same `feeCode` (`CAPA`)
- different `UFN` values (`030625/123` vs `030625/124`)

**What it proves**
- this combination is accepted by the API
- the resulting bulk submission is persisted as Legal Help

**Rule under test**
- Changing the **UFN** is enough to avoid treating the two rows as the same claim in this API-visible acceptance flow.

---

### 5. `Should have no errors when UFN differs - two submissions (<format>)`
Formats: `csv`, `txt`, `xml`

**Purpose**
- Verifies claims remain acceptable across **two separate submissions** when the **UFN** changes.

**Data pattern**
- first submission: `UCN 07081996/S/EKOT`, `UFN 040625/123`
- second submission: same `UCN`, same office `0U099L`, but `UFN 040625/124`
- each generated submission uses a **fresh submission period**, so the scenario isolates the UFN difference rather than testing period reuse

**What it proves**
- the first generated file uploads successfully
- a second file with the same UCN but different UFN is also accepted
- the second upload still produces a valid Legal Help submission summary

**Rule under test**
- Across submissions, a changed **UFN** means the later claim should not be treated as a duplicate in this API-level flow.

---

### 6. `Should have no errors when UCN differs - single submission (<format>)`
Formats: `csv`, `txt`, `xml`

**Purpose**
- Verifies two claims in the **same file** are acceptable when the **UCN** differs.

**Data pattern**
- different `UCN`
- same `feeCode` (`CAPA`)
- same `UFN` (`010625/123`)

**What it proves**
- the upload succeeds
- the bulk submission persists correctly as Legal Help

**Rule under test**
- Different **UCN** values distinguish the claims, so they should not be treated as duplicates.

---

### 7. `Should have no errors when UCN differs - two submissions (<format>)`
Formats: `csv`, `txt`, `xml`

**Purpose**
- Verifies two separate submissions are both acceptable when the **UCN** changes.

**Data pattern**
- first file uses `UCN 07081996/S/UNQA`
- second file uses `UCN 07081996/S/UNQB`
- both are for office `0U099L`
- each generated submission uses a fresh submission period

**What it proves**
- first upload succeeds
- second upload also succeeds
- the second upload yields a valid Legal Help submission summary

**Rule under test**
- Across submissions, a changed **UCN** prevents duplicate treatment in the API-visible path.

---

### 8. `Should have no errors when fee code differs - single submission (<format>)`
Formats: `csv`, `txt`, `xml`

**Purpose**
- Verifies two claims in one file are acceptable when the **fee code** differs.

**Data pattern**
- same `UCN`
- same `UFN`
- fee codes differ: `CAPA` vs `COM`

**What it proves**
- the API accepts the submission
- the persisted record is a Legal Help bulk submission

**Rule under test**
- Different **fee codes** are enough to distinguish the claims for this acceptance flow.

---

### 9. `Should have no errors when fee code differs - two submissions (<format>)`
Formats: `csv`, `txt`, `xml`

**Purpose**
- Verifies two separate submissions are acceptable when the **fee code** changes.

**Data pattern**
- same `UCN`
- different `feeCode` values (`CAPA` then `COM`)
- same office `0U099L`
- each generated submission uses a fresh submission period

**What it proves**
- first upload succeeds
- second upload also succeeds
- the second upload still produces a valid Legal Help summary

**Rule under test**
- Across submissions, a changed **fee code** stops the later claim from being treated as a duplicate in this API-level suite.

---

### 10. `Not duplicate when previous submission was invalid (<format>)`
Formats: `csv`, `txt`, `xml`

**Purpose**
- Verifies a later identical claim is allowed when the earlier submission has been marked invalid.

**What it does**
1. Generates and uploads a Legal Help file.
2. Uses `PATCH /api/v1/bulk-submissions/{id}` to force that bulk submission to `VALIDATION_FAILED`.
3. Generates a second file with the **same office, same UCN, same UFN, same fee code**.
4. Uploads the second file.

**Important implementation detail**
- The second generated file still receives a fresh submission period from the helper.
- So this scenario isolates the effect of the earlier submission being marked invalid; it is not a same-period resubmission test.

**What it proves**
- the second upload is accepted with `201`
- the second upload persists as a valid Legal Help bulk submission

**Rule under test**
- A prior bulk submission that is no longer valid should not block a fresh upload of the same claim.

**Why this matters**
- This is the one scenario that actively simulates lifecycle state changes rather than just comparing claim field combinations.

---

### 11. `Not duplicate when office is different across submissions (<format>)`
Formats: `csv`, `txt`, `xml`

**Purpose**
- Verifies duplicate checking is scoped by **office**.

**Data pattern**
- same `UCN`
- same `UFN`
- same `feeCode`
- first office: `1T102C`
- second office: `0U099L`
- each generated submission uses a fresh submission period

**What it proves**
- the first upload succeeds
- a second upload of the same claim details from a different office also succeeds
- the later upload still produces a Legal Help submission summary

**Rule under test**
- Duplicate behaviour is office-specific; the same claim across different offices is not treated as a duplicate in this API-focused suite.

---

## Summary of business rules covered

The feature mainly proves that Legal Help submissions are accepted when any of these conditions make the claim distinct enough for API-level acceptance:

- it is the **first occurrence**
- the **UFN differs**
- the **UCN differs**
- the **fee code differs**
- the earlier submission was marked **invalid**
- the **office differs**

It also proves that:

- the multipart bulk upload endpoint accepts valid Legal Help files in **CSV/TXT/XML** formats
- identical file uploads can still produce separate bulk submission records at the API boundary

## Short interpretation of the feature

If you want the one-line summary:

> `duplicateChecksLegalHelp.feature` is an API-level acceptance suite that checks Legal Help bulk submissions are created successfully when claim identity changes in ways that should avoid duplicate treatment, while deliberately leaving true downstream duplicate-validation messages to the full end-to-end suite.


