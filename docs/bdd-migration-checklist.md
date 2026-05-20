# Java Cucumber BDD Migration Checklist

This checklist tracks migration of API-focused Cucumber tests from `bulk-submission-and-fee-scheme-tests-` into Java tests in `laa-data-claims-api`.

## Scope

- Source style reference: `bulk-submission-and-fee-scheme-tests-/tests/features`
- Target module: `claims-data/service`
- Target test source set: `src/integrationTest`
- First migrated feature: `duplicateChecksLegalHelp.feature`

## Base Wiring (done)

- [x] Add Cucumber dependencies in `claims-data/service/build.gradle` (`integrationTestImplementation` scope)
- [x] Create Cucumber runner `claims-data/service/src/integrationTest/java/uk/gov/justice/laa/dstew/payments/claimsdata/bdd/CucumberIntegrationTest.java`
- [x] Create Spring bridge `claims-data/service/src/integrationTest/java/uk/gov/justice/laa/dstew/payments/claimsdata/bdd/CucumberSpringConfiguration.java`
- [x] Create initial step class `claims-data/service/src/integrationTest/java/uk/gov/justice/laa/dstew/payments/claimsdata/bdd/steps/LegalHelpDuplicateChecksSteps.java`
- [x] Add dedicated BDD feature folder `claims-data/service/src/integrationTest/resources/features/bdd`

## Migration Strategy

1. Keep scenario language close to source feature for traceability.
2. Replace UI-centric steps with API and repository assertions.
3. Migrate one scenario at a time from `@pending` to executable.
4. Keep deterministic data setup in `Given` steps and helpers.

## Recommended Folder Convention

- Features: `claims-data/service/src/integrationTest/resources/features/bdd/**/*.feature`
- Step defs: `claims-data/service/src/integrationTest/java/.../bdd/steps/*Steps.java`
- Shared step context: `claims-data/service/src/integrationTest/java/.../bdd/context/*`
- Test fixture builders/helpers: `claims-data/service/src/integrationTest/java/.../bdd/support/*`

## Step Definition Conventions

- One step class per domain area, e.g. `LegalHelpDuplicateChecksSteps`
- Keep HTTP interaction in helper methods, not inline in every step
- Prefer `MockMvc` for in-process API assertions
- Use repositories only for setup/verification not exposed via endpoint
- Use clear step wording that maps to API behavior rather than UI page actions

## Data and Assertions

- Reuse patterns from `AbstractIntegrationTest` for containerized DB and cleanup
- Seed prior submissions/claims with unique references to avoid collisions
- Assert both HTTP response shape and persisted state for duplicate outcomes
- Verify duplicate code/message consistency where available

## Incremental Execution Plan

- [ ] Convert "First occurrence is accepted" into API request + success assertions
- [ ] Convert "Duplicate detected against a previously submitted claim" into seeded duplicate + failure assertion
- [ ] Convert no-duplicate variants (`UCN`, `UFN`, `feeCode`) into separate scenario outlines
- [ ] Remove `@pending` tags as each scenario gains Java step coverage

## Gradle and Execution

If credentials/plugin access are available, run the BDD runner with:

```bash
./gradlew :claims-data:service:integrationTest --tests '*CucumberIntegrationTest'
```

If private plugin resolution is unavailable in your environment, validate files compile in the IDE and run once credentials are configured.

