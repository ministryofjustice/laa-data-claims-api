# LAA Exports Spring Boot Starter

A Spring Boot starter that generates CSV export endpoints from SQL definitions and streams results safely.

## What it does

- Generates Postgres COPY export providers and controllers at build time from YAML definitions.
- Streams CSV responses using PostgreSQL COPY TO STDOUT.
- Validates export parameters and supplies maxRows as a SQL parameter.
- Produces per-export endpoints with Swagger examples showing header rows.

## Configuration

Enable the starter:

```yaml
laa:
  springboot:
    starter:
      exports:
        enabled: true
        web:
          enabled: true
          base-path: /exports
        defaults:
          max-rows: 50000
```

## Export definitions

Place definitions in:

```
src/main/resources/export_definitions/
  submission_claims.yml
  submission_claims_count.yml
```

A single-definition file uses the filename as the export key:

```yaml
description: "Counts total claims for submissions"
roles: [ "ALL" ]
provider: submissionClaimsCountExportProvider
maxRows: 50000
sql: |
  select
    s.id as submissionId,
    count(c.id) as claimCount
  from claims.submission s
  join claims.claim c on c.submission_id = s.id
  where s.id = :submissionId
  group by s.id;
columns:
  - key: submissionId
    header: "Submission ID"
  - key: claimCount
    header: "Amount of claims"
params:
  - name: submissionId
    type: uuid
    required: true
```

You can also provide a multi-definition file using `laa.exports.definitions` (supported for compatibility).

By default, CSV columns follow the order of the SQL select list; change the SQL order to adjust column ordering.

You can override CSV header names by listing `columns` with the SQL alias as `key` and the desired `header`.


## Generated endpoints

Each definition produces an endpoint:

```
GET /exports/{key}.csv
```

Swagger examples display the CSV header row based on SQL aliases, with optional header overrides from `columns`.

## Build-time tasks

```
./gradlew :claims-data:service:generateExportSql
./gradlew :claims-data:service:generateExportControllers
```

If exports are disabled or no definitions are found, the tasks log a warning and skip generation.

## Notes

- Sorting by request parameter is not supported; add `ORDER BY` in SQL if needed.
- `max-rows` is enforced from configuration and not exposed as a request parameter.
- If no export definitions are found, Gradle logs warnings during generation.
- The application user must have access to every table referenced by the export SQL.
- Views and materialised views are encouraged to simplify definitions and to consider performance impacts.
