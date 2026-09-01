# Amendment BDD Harness (DSTEW-2301)

The **Amendment BDD Harness** is the shared scaffolding that lets any amendment story
(DSTEW-1753, 1767, 1769, 1770, 1771, 1774, …) exercise the happy-path `PATCH
/api/v1/submissions/{submissionId}/claims/{claimId}` flow end-to-end in **local** BDD mode
(no event-service, no external HTTP) and in **UAT** BDD mode alike.

Before this harness a well-formed amendment PATCH always short-circuited with
`HTTP 400 INVALID_CLAIM_BEFORE_STATE_CFD_MISSING` because no seed claim carried a
baseline `calculated_fee_detail` row, and every external call hit unresolvable URLs.

---

## What it gives you

| Piece | Class / File | Purpose |
| --- | --- | --- |
| Fixture builder | `bdd.support.AmendableClaimFixture` | Seeds a fresh `Submission` + `Claim` + `ClaimSummaryFee` + baseline `CalculatedFeeDetail` in one transaction so amendment validation reaches the "amendable" branch. |
| Mocked FSP client | `@MockitoBean FeeSchemePlatformRestClient` on `bdd.CucumberSpringConfiguration` | Replaces the real Fee-Scheme-Platform HTTP client so amendment repricing can be armed / verified without a live upstream. |
| Mocked PDA validator | `@MockitoBean ValidationService` | Replaces the Provider-Details-API `ValidationService` bean so PDA outcomes can be armed / verified. |
| Per-scenario reset | `bdd.hooks.BddAmendmentResetHook` | `@Before(order = 0)` — resets both mocks, re-applies "happy" defaults, and restores the boot-time value of `laa.claims.api.amendments.enabled` so scenario state cannot bleed. |
| Shared step glue | `bdd.steps.AmendmentHarnessCommonSteps` | The Gherkin phrases downstream stories reuse (see below). |
| Canary scenario | `resources/features/bdd/amendmentHarnessCanary.feature` | One scenario tagged `@dstew-harness-canary` — if this ever goes red on `main`, the harness itself has regressed. |

Everything lives under `claims-data/service/src/bddTest/…`. Nothing in this harness
touches `main`/production code paths.

---

## How mocking is wired (and why)

`@MockitoBean` annotations **must** be declared on the class that carries
`@CucumberContextConfiguration` — Spring's bean-override machinery only scans the test
class itself, not `@Import`ed `@Configuration` classes. That is why both mocks live on
`CucumberSpringConfiguration` rather than in `BddBeansConfiguration`.

Default answers cannot be applied from `@PostConstruct` because the mock beans are
injected **after** the `@Configuration` lifecycle fires. `BddAmendmentResetHook` fires at
`@Before(order = 0)` so:

1. mocks exist,
2. defaults land **before** any `Given the FSP service will …` step overrides them.

**Default answers** applied every scenario:

- `FeeSchemePlatformRestClient.calculateFee(any())` → `ResponseEntity.ok(new FeeCalculationResponse())`
- `FeeSchemePlatformRestClient.getFeeDetails(any())` → `ResponseEntity.ok(new FeeDetailsResponseV2())`
- `ValidationService.validateSubmission(...)` → `ValidationResult(valid = true)`
- `ValidationService.validateClaim(...)` → `ClaimValidationResult(valid = true)`

> ⚠️ `ValidationResult.valid` defaults to `false` (Java `boolean` primitive). Setting it
> to `true` explicitly in the reset hook is **load-bearing** — omit it and every
> pre-existing submission BDD scenario 400s with an empty issues list.

---

## Reference-data reset

Intentionally a **no-op** for DSTEW-2301. `AmendableClaimFixture` only writes into the
transactional submission/claim/summary-fee/CFD graph; it does not touch `fee_scheme`,
`area_of_law`, or `matter_type` rows. Downstream stories that DO mutate ref-data must
extend `BddAmendmentResetHook` with an explicit ref-data reset — **do not** silently pile
ref-data clean-up in there or you'll add cost to every non-amendment scenario.

---

## Standing rules (must obey when using the harness)

1. Wrap every step body in `BddStepFailures.step(context, () -> {...})`. No naked
   assertions escape into cucumber's report — the harness expects wrapped failures.
2. **No silent de-scopes.** Type 1/2 broken scenarios → comment out with a `# TODO
   DSTEW-xxxx` marker. Type 3 (dead) → delete + renumber Examples.
3. Any new outbound-call verification phrase goes on `AmendmentHarnessCommonSteps` so
   there is one owner per phrase across the codebase.
4. If a scenario needs a non-default FSP or PDA answer, arm the mock with an explicit
   `Given the …` step in the scenario, not in a bean initialiser. The reset hook wipes
   any static / boot-time mock stubbing at the start of every scenario.

---

## Shared step glue owned here

Owned by `AmendmentHarnessCommonSteps` (see class for exact phrasing):

**Given**
- `a fresh amendable claim on a legal-help submission at version {long}`
- `the PDA service will respond "{string}" within the amendment-path timeout`
- `the FSP service will return a valid fee calculation for the amendment`
- `the FSP service will fail with HTTP {int}`

**When**
- `I submit a well-formed non-pricing amendment`

**Then — outcome**
- `the amendment is accepted`
- `claim.version is now {long}`
- `claim.is_amended is true`
- `exactly one claim_amendment row was inserted for this claim`
- `no claim_amendment record was inserted for this claim by this attempt`
- `no FSP-derived calculated_fee_detail row was inserted for this claim by this attempt`
- `the claim persisted state matches the pre-amendment state`

**Then — outbound-call verification**
- `no outbound PDA call was made`
- `exactly {int} outbound PDA call was made`
- `no outbound FSP call was made`
- `exactly {int} outbound FSP call was made`

`AmendmentPdaTriggerSteps#noOutboundPdaCallWasMade` was a log-only spec-guard prior to
DSTEW-2301 — it has been removed and ownership moved to the harness so the phrase now
performs a real `verify(pdaValidationService, never()).validate(any())`.

---

## Running

```bash
# Canary only — smoke-test the harness itself
./gradlew :claims-data:service:bddTest -Dcucumber.filter.tags="@dstew-harness-canary"

# Full BDD (194 scenarios today, ~55s locally)
./gradlew :claims-data:service:bddTest

# UAT mode (real event-service on localhost:8080, real HTTP fidelity)
./gradlew :claims-data:service:bddTest -Dbdd.mode=uat
```

The CI pipeline (`.github/workflows/deploy-main.yml → :claims-data:service:bddTest`)
runs in local mode by default. The harness therefore covers CI **and** local runs.

---

## Timings (before / after DSTEW-2301)

| | Cucumber time | Wall-clock | Tests | Failures |
| --- | ---: | ---: | ---: | ---: |
| `origin/main` baseline | 101.5 s | 105 s | 193 | 0 |
| `DSTEW-2301-bdd` HEAD | 52.9 s | 56 s | 194 (+1 canary) | 0 |

Speed-up (~48 %) comes from mocking away slow / timing-out external HTTP calls. No
scenarios were skipped or dropped.

---

## Extending the harness

When a new amendment story needs harness support:

1. If it needs a new default mock behaviour → add it to `BddAmendmentResetHook.applyDefaults()`
   with a comment naming the ticket that requires it.
2. If it needs a new arming phrase (`Given the FSP service will …`) → add it to
   `AmendmentHarnessCommonSteps`, not to the story's own step class.
3. If it needs additional seed shape (assessment claim, duplicate sibling, non-legal-help
   area of law) → extend `AmendableClaimFixture.Builder`. Keep the DSL fluent.
4. If it needs ref-data mutation → **add an explicit ref-data reset method to the hook
   and gate it behind a tag** so non-amendment scenarios don't pay the cost.

Every extension lands with:
- a test in `amendmentHarnessCanary.feature` (or a new sibling canary) that would fail
  if the extension regressed,
- a note in this README.

---

Ticket: **DSTEW-2301**.

