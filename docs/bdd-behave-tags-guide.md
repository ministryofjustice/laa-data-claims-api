# BDD Tag Run Guide (Behave-Style)

This project runs BDD tests with **Cucumber (JUnit Platform)**, but tag filtering works the same way as Behave-style tag expressions.

## Quick Start

Run from repo root:

```zsh
cd /path/to/laa-data-claims-api
```

Run all BDD scenarios in `features/bdd`:

```zsh
./gradlew :claims-data:service:bddTest
```

## Run By Tags

Use `-Dcucumber.filter.tags='EXPRESSION'`.

### Regression packs

Every feature file carries `@Full_Regression` at feature level, so the whole
suite is selectable with one tag. A curated subset of end-to-end happy-path
scenarios (max 20% of executed scenarios, Scenario Outlines counted per
example row) also carries `@Regression` at scenario level.

| Tag | Contents | Where it runs |
|---|---|---|
| `@Regression` | E2E happy-path pack (~20% of the suite) | PRs (`deploy-uat-preview.yml`) and `main` (`deploy-main.yml`) |
| `@Full_Regression` | Every BDD scenario | Nightly Mon–Fri + manual dispatch (`bdd-full-regression-nightly.yml`) |

```zsh
./gradlew :claims-data:service:bddTest -Dcucumber.filter.tags='@Regression'
./gradlew :claims-data:service:bddTest -Dcucumber.filter.tags='@Full_Regression'
```

When adding a new scenario, leave it in `@Full_Regression` only (inherited from
the feature) unless it is a key E2E happy path; add `@Regression` to it only if
the pack stays within the 20% cap.

### Other examples

Run only `@api` scenarios:

```zsh
./gradlew :claims-data:service:bddTest -Dcucumber.filter.tags='@api'
```

Run only `@pending` scenarios:

```zsh
./gradlew :claims-data:service:bddTest -Dcucumber.filter.tags='@pending'
```

Run all except `@pending`:

```zsh
./gradlew :claims-data:service:bddTest -Dcucumber.filter.tags='not @pending'
```

Run tags with logical operators:

```zsh
./gradlew :claims-data:service:bddTest -Dcucumber.filter.tags='@duplicateChecks and @api'
./gradlew :claims-data:service:bddTest -Dcucumber.filter.tags='@api and not @pending'
./gradlew :claims-data:service:bddTest -Dcucumber.filter.tags='@duplicateChecks or @bulkSubmission'
```

## Run A Single Feature File

```zsh
./gradlew :claims-data:service:bddTest -Dcucumber.features='classpath:features/bdd/duplicateChecksLegalHelp.feature'
```


## Notes

- Tag expressions support: `and`, `or`, `not`, parentheses.
- Tags are read from `.feature` files (Feature, Scenario, Scenario Outline).
- This command was verified locally:

```zsh
./gradlew :claims-data:service:bddTest -Dcucumber.filter.tags='@api'
```
