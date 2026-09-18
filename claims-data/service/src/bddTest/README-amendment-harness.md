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
| Real FSP client + MockServer | `FeeSchemePlatformRestClient` (real bean) + `bdd.support.BddMockServerSupport` | **DSTEW-2353:** the Fee-Scheme-Platform client is the **real** HTTP client — no longer a `@MockitoBean`. Its base URL (`${FEE_SCHEME_PLATFORM_API_URL}`) is pointed at a shared `MockServerContainer` started by `CucumberSpringConfiguration`. Because the client is annotated `@HttpExchange("/api")`, repricing is armed / verified as real `POST /api/v1/fee-calculation` + `GET /api/v2/fee-details/…` traffic via `BddMockServerSupport` (same `/api/…` paths the claims-validation-core client uses). |
| PDA validator spy | `@MockitoSpyBean ValidationService` on `bdd.CucumberSpringConfiguration` | A Mockito **spy** (runs the real facade unless a method is stubbed) so PDA outcomes can be armed / verified; the PDA `/schedules` HTTP is also stubbed on the shared MockServer. |
| Per-scenario reset | `bdd.hooks.BddAmendmentResetHook` + `BddHooks` | `BddAmendmentResetHook` (`@Before(order = -2)`) resets the `ValidationService` / persistence **spies** and re-applies their "happy" defaults. `BddHooks.resetScenarioContextAndData()` (`order = 0`) resets the MockServer and seeds the FSP happy-path default (`bddMockServerSupport.stubAmendmentFspOk()`), and owns the amendments feature-flag reset. |
| Shared step glue | `bdd.steps.AmendmentHarnessCommonSteps` | The Gherkin phrases downstream stories reuse (see below). |
| Canary scenario | `resources/features/bdd/amendmentHarnessCanary.feature` | One scenario tagged `@dstew-harness-canary` — if this ever goes red on `main`, the harness itself has regressed. |

Everything lives under `claims-data/service/src/bddTest/…`. Nothing in this harness
touches `main`/production code paths.

---

## How external transports are wired (and why)

There are **two different mechanisms**, one per transport.

**Fee Scheme Platform (FSP) — real client over MockServer (DSTEW-2353).**
`FeeSchemePlatformRestClient` is the real bean (no longer a `@MockitoBean`).
`CucumberSpringConfiguration` starts a shared `MockServerContainer` and points the client's base
URL (`${FEE_SCHEME_PLATFORM_API_URL}`) at it via `@DynamicPropertySource`, so amendment repricing
makes **real HTTP** calls that MockServer answers. The client is annotated `@HttpExchange("/api")`,
so it hits `/api/v2/fee-details/…` and `/api/v1/fee-calculation` — the same `/api/…` paths the
claims-validation-core client uses. Stub/verify helpers live in `bdd.support.BddMockServerSupport`;
the happy-path default (`stubAmendmentFspOk()`) is seeded every scenario from
`BddHooks.resetScenarioContextAndData()` (order 0), right after `bddMockServerSupport.reset()`. A
scenario overrides it with an explicit arming step (e.g. `Given the FSP service will fail with HTTP
500`, which clears the fee-calculation expectation and replaces it).

**Validation facade / persistence — Mockito spies.**
`ValidationService` and `ClaimAmendmentPersistenceService` are `@MockitoSpyBean` on
`CucumberSpringConfiguration` (spies run the real object unless a method is stubbed). Spy
annotations **must** live on the class carrying `@CucumberContextConfiguration` — Spring's
bean-override machinery only scans the test class itself, not `@Import`ed `@Configuration` classes.
Defaults cannot be applied from `@PostConstruct` because the spy beans are injected **after** the
`@Configuration` lifecycle fires, so `BddAmendmentResetHook` fires at `@Before(order = -2)` to reset
the spies and re-apply defaults before `BddHooks` (order 0) truncates repositories.

**Default answers** applied every scenario:

- FSP `GET /api/v2/fee-details/…` and `POST /api/v1/fee-calculation` → 200 (MockServer, via
  `stubAmendmentFspOk()` seeded from `BddHooks`)
- `ValidationService.validateSubmission(...)` → `ValidationResult(valid = true)` (spy default)
- `ValidationService.validateClaim(...)` → `ClaimValidationResult(valid = true)` (spy default)

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
4. If a scenario needs a non-default FSP or PDA answer, arm it with an explicit
   `Given the …` step in the scenario, not in a bean initialiser. FSP arming re-stubs the
   MockServer expectation; PDA arming stubs the `ValidationService` spy. The per-scenario reset
   (`BddHooks` clears MockServer, `BddAmendmentResetHook` resets the spies) wipes any prior
   stubbing at the start of every scenario.

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
- `I submit a well-formed pricing amendment` (changes `case_start_date`, a pricing-impacting FSP
  request-body field, so a real `POST /api/v1/fee-calculation` fires — see
  `amendmentsFspRepricingHttp.feature`)

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
- `no outbound FSP call was made from the amendment harness` (a MockServer request-count check on
  `POST /api/v1/fee-calculation`)
- `exactly {int} outbound FSP call was made` (MockServer request-count on the same endpoint)

`AmendmentPdaTriggerSteps#noOutboundPdaCallWasMade` was a log-only spec-guard prior to
DSTEW-2301 — it has been removed and ownership moved to the harness so the phrase now
performs a real `verify(validationService, never()).validateClaim(any(), any())`. That
2-arg overload is the one the amendment path calls
(`AmendmentExternalValidationStep.java` line 85: `validationService.validateClaim(claim,
validationCodes)`); asserting on the 3-arg overload would silently pass even when a
real PDA call happened, so the harness fixes on the exact overload production uses.

---

## Running

```bash
# Canary only — smoke-test the harness itself
./gradlew :claims-data:service:bddTest -Dcucumber.filter.tags="@dstew-harness-canary"

# Full BDD (194 scenarios today, ~55s locally)
./gradlew :claims-data:service:bddTest

# UAT mode — real event-service on localhost:8080 (see note below)
./gradlew :claims-data:service:bddTest -Dbdd.mode=uat
```

> **Note on `bdd.mode=uat`.** UAT mode currently changes only the
> event-service target: BDD scenarios hit a real event-service on
> `localhost:8080` instead of the local application's in-process handler.
> The **FSP client is real HTTP against the shared MockServer** in both
> modes (DSTEW-2353). `ValidationService` remains a `@MockitoSpyBean`;
> making the aggregate validation facade a fully real transport in UAT
> mode is out of scope and tracked under the follow-up validation-coverage
> story (DSTEW-2317).

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

1. If it needs a new default behaviour → for FSP add a MockServer stub in
   `BddMockServerSupport` (and seed it from `BddHooks` if it must apply every scenario); for the
   `ValidationService` spy add it to `BddAmendmentResetHook.applyDefaults()`. Comment the ticket
   that requires it.
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

