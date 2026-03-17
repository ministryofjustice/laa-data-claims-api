# Bruno collection policy (UI-first)

This folder contains the Bruno collection for `laa-data-claims-api`.

## Before you start

Make sure you have:

- Bruno desktop app installed.
- This repository checked out locally.
- The API running in the target environment (for local use, usually `localhost:8080`).
- A valid auth token to use as `AUTH_TOKEN`.

If you do not have a valid token or environment access, ask the team first.

## Overview

Bruno is a Git-friendly, offline-first API client that stores requests as plain-text `.bru` files.

Using Bruno for this project helps by:

- making API checks easy to run from a consistent UI flow,
- keeping request definitions version-controlled and reviewable,
- reducing environment-specific drift through shared variables and setup requests,
- speeding up onboarding with reusable, documented request scenarios.

Maintaining this collection well means teams can debug faster, reproduce issues more reliably, and keep API behaviour checks consistent across environments.

## Working principles

- Use Bruno UI for daily work (open collection, select environment, edit variable values, run requests).
- Before commit, review generated `.bru` diffs manually for quality and portability.
- Convert machine-specific absolute upload paths to repo-relative paths before pushing.

## Quick start (UI)

1. Install Bruno from https://www.usebruno.com/downloads
2. In Bruno, open the collection folder `bruno/` (the folder containing `bruno.json`).
3. Select target environment (`LOCAL`, `FEATURE`, `UAT`) from the environment selector.
4. Open the environment editor in Bruno UI and set required values:
   - `HOST` (host:port, no protocol)
   - `AUTH_TOKEN` (secret)
5. Run setup requests in order, then run the API requests you need.

Expected result:

- Setup requests populate IDs such as `SUBMISSION_ID` and `CLAIM_ID`.
- You can then run requests under `api/v1/...` without manually copying IDs.

Helpful docs:

- Download/install: https://docs.usebruno.com/bruno-basics/download
- Create/open collection: https://docs.usebruno.com/bruno-basics/create-a-collection
- Import collections: https://docs.usebruno.com/import-export-data/import-collections
- Run a collection (UI): https://docs.usebruno.com/bruno-basics/run-a-collection

## Environment values (UI, not file edits)

Set values in the Bruno UI environment editor.

Required:

- `HOST` (host:port, no protocol)
- `AUTH_TOKEN` (secret bearer token)

Runtime values (set by setup scripts):

- `SUBMISSION_ID`
- `CLAIM_ID`
- `ASSESSMENT_ID`
- `BULKSUBMISSION_ID`

If setup is skipped, dependent requests can fail because IDs are missing. If needed, you can run requests manually by setting environment variables in the UI or by supplying values directly in path parameters.

Tip for first-time users:

- Do not edit `bruno/environments/*.bru` to set normal values.
- Use the Bruno environment UI so you can switch environments safely.

Helpful docs:

- Variables overview: https://docs.usebruno.com/variables/overview
- Environment variables: https://docs.usebruno.com/variables/environment-variables
- Secret variables: https://docs.usebruno.com/secrets-management/secret-variables
- Variable interpolation: https://docs.usebruno.com/variables/interpolation

## Setup folder: why it must run first

Purpose: seed IDs into environment variables so downstream requests work without manual copy/paste.

Execution order:

1. `SETUP: Grab valid submission id`
   - Creates valid bulk submission.
   - Writes `SUBMISSION_ID` and `BULKSUBMISSION_ID`.
2. `SETUP: Grab invalid submission id`
   - Creates invalid-scenario submission.
   - Writes `SUBMISSION_ID`.
3. `SETUP: Grab claim id from submission`
   - Reads submission by `SUBMISSION_ID`.
   - Writes `CLAIM_ID`.

Path portability rule:

- Bruno UI will save upload file paths as absolute local paths.
- Bruno UI does not currently provide a feature to convert those file paths to repo-relative paths.
- Before commit, manually edit the request `.bru` file and change upload paths to repo-relative values..

Helpful docs:

- Send requests overview: https://docs.usebruno.com/send-requests/overview
- REST editor overview: https://docs.usebruno.com/send-requests/REST/overview
- Parameters: https://docs.usebruno.com/send-requests/REST/parameters
- Headers: https://docs.usebruno.com/send-requests/REST/req-header
- Body data (including file uploads): https://docs.usebruno.com/send-requests/REST/body-data
- Auth overview: https://docs.usebruno.com/auth/overview
- Bearer auth: https://docs.usebruno.com/auth/bearer

## Update and organisation rules

- Place requests by API resource under `bruno/api/v1/<resource>/...`.
- Keep setup/bootstrap requests under `bruno/setup/` and prefix with `SETUP:`.
- Keep exports under `bruno/exports/`.
- Keep docs/utility checks under `bruno/swagger-ui/`.
- Keep names explicit and scenario-based (for example: `get claim`, `update submission invalid status`).
- Use `auth: inherit` unless there is a clear exception.
- Add tests/scripts when a response value must feed later requests.

## How to amend this collection (UI workflow)

Use this flow when adding or changing requests:

1. In Bruno UI, right-click the target folder and create or duplicate a request.
2. Update method, path, params, headers, and body in the request editor.
3. Keep variables dynamic (`{{HOST}}`, `{{SUBMISSION_ID}}`, etc.) rather than hard-coding values.
4. If later requests need response data, add a test script to set env vars (for example with `bru.setEnvVar(...)`).
5. Save in Bruno, then review the generated `.bru` diff in Git.
6. If file uploads were used, manually convert absolute file paths to repo-relative paths in the `.bru` file before commit.
7. Confirm naming and folder placement follow the rules above.

Helpful docs:

- Create request: https://docs.usebruno.com/bruno-basics/create-a-request
- Create test: https://docs.usebruno.com/bruno-basics/create-a-test
- Test intro: https://docs.usebruno.com/testing/tests/introduction
- Script variables/chaining: https://docs.usebruno.com/testing/script/vars
- JavaScript API reference (`bru.setEnvVar`, etc.): https://docs.usebruno.com/testing/script/javascript-reference
- Bru tag reference: https://docs.usebruno.com/bru-lang/tag-reference
