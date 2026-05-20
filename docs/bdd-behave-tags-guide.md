# BDD Tag Run Guide (Behave-Style)

This project runs BDD tests with **Cucumber (JUnit Platform)**, but tag filtering works the same way as Behave-style tag expressions.

## Quick Start

Run from repo root:

```zsh
cd /Users/raza.ahmed/IdeaProjects/laa-data-claims-api
```

Run all BDD scenarios in `features/bdd`:

```zsh
./gradlew :claims-data:service:integrationTest --tests '*CucumberIntegrationTest'
```

## Run By Tags

Use `-Dcucumber.filter.tags='EXPRESSION'`.

Run only `@api` scenarios:

```zsh
./gradlew :claims-data:service:integrationTest --tests '*CucumberIntegrationTest' -Dcucumber.filter.tags='@api'
```

Run only `@pending` scenarios:

```zsh
./gradlew :claims-data:service:integrationTest --tests '*CucumberIntegrationTest' -Dcucumber.filter.tags='@pending'
```

Run all except `@pending`:

```zsh
./gradlew :claims-data:service:integrationTest --tests '*CucumberIntegrationTest' -Dcucumber.filter.tags='not @pending'
```

Run tags with logical operators:

```zsh
./gradlew :claims-data:service:integrationTest --tests '*CucumberIntegrationTest' -Dcucumber.filter.tags='@duplicateChecks and @api'
./gradlew :claims-data:service:integrationTest --tests '*CucumberIntegrationTest' -Dcucumber.filter.tags='@api and not @pending'
./gradlew :claims-data:service:integrationTest --tests '*CucumberIntegrationTest' -Dcucumber.filter.tags='@duplicateChecks or @bulkSubmission'
```

## Run A Single Feature File

```zsh
./gradlew :claims-data:service:integrationTest --tests '*CucumberIntegrationTest' -Dcucumber.features='classpath:features/bdd/duplicateChecksLegalHelp.feature'
```

## Notes

- Tag expressions support: `and`, `or`, `not`, parentheses.
- Tags are read from `.feature` files (Feature, Scenario, Scenario Outline).
- This command was verified locally:

```zsh
./gradlew :claims-data:service:integrationTest --tests '*CucumberIntegrationTest' -Dcucumber.filter.tags='@api'
```

