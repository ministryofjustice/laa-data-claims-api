# PATCH Null Semantics — Implementation Guide

## Problem Statement

`PATCH /api/v1/submissions/{submission-id}/claims/{claim-id}` accepts a `ClaimPatch` request body.
The mapper uses `NullValuePropertyMappingStrategy.IGNORE`, which means:

| Scenario | Java value | Result |
|---|---|---|
| Field **not sent** in request | `null` | Ignored — existing value preserved ✅ |
| Field **sent as `null`** in request | also `null` | Also ignored — cannot clear the field ❌ |

There is no way to distinguish the two cases, so callers cannot intentionally clear a field value.

---

## Option A — `JsonNullable<T>` on `ClaimPatch` only

### What it is

`JsonNullable<T>` is a wrapper type from `org.openapitools:jackson-databind-nullable`.
A field can be in one of three states:

| State | Meaning |
|---|---|
| `JsonNullable.undefined()` | Field was not present in the request — do not touch the existing value |
| `JsonNullable.of(null)` | Field was explicitly sent as `null` — clear the existing value |
| `JsonNullable.of(value)` | Field was sent with a value — replace the existing value |

### Pros

- Industry-standard, idiomatic solution for this exact problem
- Clean semantics expressed directly in the model
- `jackson-databind-nullable` is already a declared dependency in `claims-data/api/build.gradle`
- MapStruct has first-class support via `JsonNullableMapper`

### Cons

- `ClaimPatch` is a generated file — it must be excluded from the generator to prevent being overwritten on the next `openApiGenerate` run
- Every caller (event service, tests) that constructs a `ClaimPatch` must update how they set fields
- `ClaimMapper.updateSubmissionClaimFromPatch` must be updated to unwrap `JsonNullable`

---

### Step-by-step

#### Step 1 — Protect `ClaimPatch` from being regenerated

File: `claims-data/api/generated/.openapi-generator-ignore`

Add the following line at the bottom:

```
src/main/java/uk/gov/justice/laa/dstew/payments/claimsdata/model/ClaimPatch.java
```

This tells the OpenAPI generator to never overwrite that file.

---

#### Step 2 — Rewrite `ClaimPatch.java`

File: `claims-data/api/generated/src/main/java/uk/gov/justice/laa/dstew/payments/claimsdata/model/ClaimPatch.java`

1. Remove these imports:
   ```java
   import org.springframework.lang.Nullable;
   import jakarta.annotation.Generated;
   ```

2. Add this import:
   ```java
   import org.openapitools.jackson.nullable.JsonNullable;
   ```

3. Change every field declaration from:
   ```java
   private @Nullable String feeCode;
   ```
   to:
   ```java
   private JsonNullable<String> feeCode = JsonNullable.undefined();
   ```
   Apply the same pattern for all other types (`Integer`, `Boolean`, `BigDecimal`, enum types, etc.).

4. Change every getter return type from `T` to `JsonNullable<T>`:
   ```java
   // Before
   public String getFeeCode() { return feeCode; }

   // After
   public JsonNullable<String> getFeeCode() { return feeCode; }
   ```

5. Change every setter parameter type from `T` to `JsonNullable<T>`:
   ```java
   // Before
   public void setFeeCode(String feeCode) { this.feeCode = feeCode; }

   // After
   public void setFeeCode(JsonNullable<String> feeCode) { this.feeCode = feeCode; }
   ```

6. Update fluent builder methods in the same way.

7. **Do not change** `feeCalculationResponse` or `validationMessages` — these are sub-objects
   that are set in full or omitted entirely; they are never individually nulled out.

---

#### Step 3 — Register `JsonNullableModule` with Jackson

File: `claims-data/service/src/main/java/uk/gov/justice/laa/dstew/payments/claimsdata/config/JacksonMappingConfig.java`

Add the following bean so Jackson can deserialise `JsonNullable` fields:

```java
import org.openapitools.jackson.nullable.JsonNullableModule;

@Bean
public JsonNullableModule jsonNullableModule() {
    return new JsonNullableModule();
}
```

---

#### Step 4 — Add the MapStruct JsonNullable extension dependency

File: `claims-data/service/build.gradle`

Add to the `dependencies` block:

```groovy
implementation 'org.mapstruct.extensions.spring:mapstruct-spring-extensions:1.1.2'
annotationProcessor 'org.mapstruct.extensions.spring:mapstruct-spring-extensions:1.1.2'
```

---

#### Step 5 — Update `ClaimMapper`

File: `claims-data/service/src/main/java/uk/gov/justice/laa/dstew/payments/claimsdata/mapper/ClaimMapper.java`

1. Add `JsonNullableMapper.class` to the `uses` list on `@Mapper`:
   ```java
   import org.mapstruct.extensions.spring.converter.JsonNullableMapper;

   @Mapper(
       componentModel = "spring",
       uses = {GlobalStringMapper.class, GlobalDateTimeMapper.class, JsonNullableMapper.class},
       ...)
   ```

2. On `updateSubmissionClaimFromPatch`, remove the `@BeanMapping` annotation:
   ```java
   // Remove this line:
   @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
   ```
   MapStruct will now automatically:
   - Skip `JsonNullable.undefined()` fields (field not sent)
   - Write `null` to the entity when `JsonNullable.of(null)` is received (explicit clear)
   - Write the unwrapped value when `JsonNullable.of(value)` is received

---

#### Step 6 — Update all callers that construct a `ClaimPatch`

Search the codebase for all usages of `ClaimPatch`:

```bash
grep -rn "ClaimPatch" --include="*.java" claims-data/service/src
```

For every place that sets a field, wrap the value in `JsonNullable.of(...)`:

```java
// Before
new ClaimPatch().status(ClaimStatus.VALID).feeCode("FC001")

// After
import org.openapitools.jackson.nullable.JsonNullable;

new ClaimPatch()
    .status(JsonNullable.of(ClaimStatus.VALID))
    .feeCode(JsonNullable.of("FC001"))
```

To explicitly clear a field, pass `JsonNullable.of(null)`:

```java
new ClaimPatch().feeCode(JsonNullable.of(null))
```

---

#### Step 7 — Verify

```bash
./gradlew :claims-data:api:compileJava
./gradlew :claims-data:service:compileJava
./gradlew :claims-data:service:test
```

Fix any remaining compilation errors — these will be callers that still use the old plain-type setters.

---

## Option B — `fieldsToNull` list on `ClaimPatch`

### What it is

Add one extra field to `ClaimPatch`:

```json
{
  "status": "VALID",
  "fields_to_null": ["fee_code", "maat_id"]
}
```

The service reads this list after the mapper has run and explicitly nulls the named fields on the entity.

### Pros

- Minimal change — all existing `ClaimPatch` fields stay as `@Nullable T`
- No new dependencies required
- No MapStruct changes needed
- Callers that do not need to null anything are completely unaffected

### Cons

- Field names are strings — typos silently do nothing (no compile-time safety)
- Requires a manual "apply nulls" step in `ClaimService.updateClaim` after the mapper
- You must decide and document which fields are permitted to be nulled
- Leaks internal field names into the API contract

---

### Step-by-step

#### Step 1 — Add `fieldsToNull` to `ClaimPatch`

File: `claims-data/api/generated/src/main/java/uk/gov/justice/laa/dstew/payments/claimsdata/model/ClaimPatch.java`

Add a new field, getter, and setter:

```java
@JsonProperty("fields_to_null")
private List<String> fieldsToNull = new ArrayList<>();

public List<String> getFieldsToNull() { return fieldsToNull; }
public void setFieldsToNull(List<String> fieldsToNull) { this.fieldsToNull = fieldsToNull; }
public ClaimPatch fieldsToNull(List<String> fieldsToNull) { this.fieldsToNull = fieldsToNull; return this; }
```

Add a comment listing all permitted field names.

> **Note:** Also add this field to `.openapi-generator-ignore` or the generated file will lose it on
> the next `openApiGenerate` run (same as Step 1 of Option A).

---

#### Step 2 — Apply nulls in `ClaimService.updateClaim`

File: `claims-data/service/src/main/java/uk/gov/justice/laa/dstew/payments/claimsdata/service/ClaimService.java`

After the call to `claimMapper.updateSubmissionClaimFromPatch(claimPatch, claim)`, add:

```java
if (claimPatch.getFieldsToNull() != null) {
    for (String field : claimPatch.getFieldsToNull()) {
        switch (field) {
            case "fee_code"  -> claim.setFeeCode(null);
            case "maat_id"   -> claim.setMaatId(null);
            case "dscc_number" -> claim.setDsccNumber(null);
            // add all other nullable fields here
            default -> log.warn("Unrecognised field in fieldsToNull: {}", field);
        }
    }
}
```

Repeat for fields that live on related entities (`Client`, `ClaimCase`), fetching the entity first.

---

#### Step 3 — Update tests

Add tests that verify:

1. A field is cleared when its snake_case name appears in `fieldsToNull`.
2. The field is preserved when absent from the list.
3. An unrecognised field name is ignored (warning logged, no exception).

---

## Comparison

| | Option A (`JsonNullable`) | Option B (`fieldsToNull`) |
|---|---|---|
| Type-safe | ✅ Yes | ❌ No — strings |
| Idiomatic / standard | ✅ Yes | ❌ No |
| Effort | Medium | Low |
| Impact on existing callers | High — all callers must update | Low — only callers needing to null |
| New dependencies | MapStruct extension | None |
| Risk of silent bugs | Low | High — typos ignored silently |
| Long-term maintainability | High | Low |

**Option A** is the correct long-term solution.
**Option B** is a viable short-term workaround if the caller-update effort of Option A is too high right now.

