# BDD Test Run Guide and GitHub Actions Integration

This guide explains how to run BDD tests for `laa-data-claims-api` locally and how to wire them into GitHub CI.

## 1) Run BDD tests locally

From repository root:

```zsh
cd /Users/raza.ahmed/IdeaProjects/laa-data-claims-api
```

### Run full BDD suite

```zsh
./gradlew :claims-data:service:bddTest
```

If you see intermittent container startup issues locally, retry with:

```zsh
./gradlew :claims-data:service:bddTest --no-daemon
```

### Run only API-tagged scenarios

```zsh
./gradlew :claims-data:service:bddTest -Dcucumber.filter.tags='@api'
```

### Run only duplicate-check API scenarios

```zsh
./gradlew :claims-data:service:bddTest -Dcucumber.filter.tags='@duplicateChecks and @api'
```

### Run all scenarios except pending

```zsh
./gradlew :claims-data:service:bddTest -Dcucumber.filter.tags='not @pending'
```

### Run one feature file

```zsh
./gradlew :claims-data:service:bddTest -Dcucumber.features='classpath:features/bdd/duplicateChecksLegalHelp.feature'
```

### Run the Cucumber test class directly

```zsh
./gradlew :claims-data:service:bddTest --tests 'uk.gov.justice.laa.dstew.payments.claimsdata.bdd.CucumberBddTest'
```

## 2) Prerequisites for local run

- Docker available/running (BDD tests use Testcontainers).
- Java available (project workflows currently target Java 25).
- GitHub package credentials available for Gradle plugin resolution (`~/.gradle/gradle.properties` or environment variables), as already described in `README.md`.

## 3) Integrating BDD tests in GitHub Actions

You currently use reusable workflows in `.github/workflows/` (for example `build-main.yml` uses `gradle-build-and-publish.yml`).

There are two practical ways to integrate BDD tests:

### Option A: Add a dedicated BDD workflow (recommended for clear visibility)

Create `.github/workflows/bdd-test-pr.yml`:

```yaml
name: BDD tests (PR)

on:
  pull_request:
    branches: [ main ]
    types: [ opened, synchronize, reopened ]

permissions:
  contents: read

jobs:
  bdd-test:
    runs-on: ubuntu-latest
    timeout-minutes: 45

    steps:
      - name: Checkout
        uses: actions/checkout@de0fac2e4500dabe0009e67214ff5f5447ce83dd # v6.0.2

      - name: Set up JDK 25
        uses: actions/setup-java@5eea5cde7a4d9c6734419ef5e2f7f0b1d0d77323 # v5.0.0
        with:
          distribution: corretto
          java-version: '25'
          cache: gradle

      - name: Run BDD tests
        env:
          GITHUB_ACTOR: ${{ github.actor }}
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
        run: |
          ./gradlew :claims-data:service:bddTest --no-daemon

      - name: Upload BDD test report
        if: always()
        uses: actions/upload-artifact@65462800fd760344b1a7b4382951275a0abb4808 # v4.3.3
        with:
          name: bdd-test-report
          path: |
            claims-data/service/build/reports/tests/bddTest/**
            claims-data/service/build/test-results/bddTest/**
```

Why this option:
- keeps BDD signal separate from unit/integration stages
- easy to make required in branch protection
- easy to retain and inspect reports as artifacts

### Option B: Add BDD task into existing reusable build workflow

If your reusable workflow supports adding extra Gradle verification tasks, include `:claims-data:service:bddTest` in that workflow configuration so it runs as part of the standard build gate.

Because the reusable workflow is version-pinned and maintained in another repository, confirm accepted input parameters before changing `build-main.yml`.

## 4) Branch protection recommendation

After creating Option A workflow:

1. Run one PR to ensure the check name appears (for example `BDD tests (PR) / bdd-test`).
2. In GitHub branch protection for `main`, mark that check as required.
3. Keep build and BDD checks separate so failures are easier to triage.

## 5) Useful CI variants

### Fast smoke run for PRs

```zsh
./gradlew :claims-data:service:bddTest --no-daemon -Dcucumber.filter.tags='@api and @smoke_single'
```

### Full run on merge/nightly

```zsh
./gradlew :claims-data:service:bddTest --no-daemon
```

A common pattern is:
- PR: smoke or focused tag subset
- main/nightly: full BDD suite

## 6) Troubleshooting notes

- If LocalStack container startup is flaky, use `--no-daemon` first.
- Always upload `build/reports/tests/bddTest` and `build/test-results/bddTest` as artifacts for failed runs.
- If plugin resolution fails in CI, verify `GITHUB_TOKEN`/package access and repository permissions.

