package uk.gov.justice.laa.dstew.payments.claimsdata.bdd.hooks;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.reset;

import io.cucumber.java.Before;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import uk.gov.justice.laa.dstew.payments.claims.validation.core.model.ValidationResult;
import uk.gov.justice.laa.dstew.payments.claims.validation.core.service.ValidationService;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.persistence.ClaimAmendmentPersistenceService;

/**
 * Cucumber {@code @Before} glue that resets the amendment-harness test doubles ({@link
 * ValidationService} spy and the {@code ClaimAmendmentPersistenceService} spy) and reapplies safe
 * defaults before every scenario. The Fee Scheme Platform client is no longer mocked here — it is
 * exercised as real HTTP against the shared MockServer (see {@code BddMockServerSupport}).
 *
 * <p><b>Ordering</b>: this hook runs at {@code order = -1} so it fires <em>before</em> {@link
 * BddHooks#resetScenarioContextAndData()} (which is {@code order = 0}). That matters for two
 * reasons:
 *
 * <ul>
 *   <li>The Mockito reset + default answers must land before any repository-truncation logic that
 *       could indirectly trigger a mocked service call.
 *   <li>Any downstream {@code Given the FSP service will …} arming step must overwrite our
 *       defaults, not the other way round; a strictly lower order guarantees that.
 * </ul>
 *
 * <p><b>Feature-flag reset is NOT owned here.</b> {@link BddHooks} already resets {@code
 * laa.claims.api.amendments.enabled} to {@code null} at {@code order = 0}. Duplicating that work
 * would just race and confuse ownership.
 *
 * <p><b>Spy beans</b>: {@link ValidationService} and {@code ClaimAmendmentPersistenceService} are
 * declared as {@code @MockitoSpyBean} directly on {@link
 * uk.gov.justice.laa.dstew.payments.claimsdata.bdd.CucumberSpringConfiguration} because Spring's
 * bean-override machinery only picks up those annotations from the test class that carries
 * {@code @CucumberContextConfiguration}. Defaults cannot be applied via {@code @PostConstruct} on
 * that configuration because the spy beans are wired later; this Cucumber hook is the first
 * guaranteed-safe touch-point.
 *
 * <p><b>Reference-data reset</b>: intentionally a no-op. The T2 fixture ({@code
 * AmendableClaimFixture}) only writes into the transactional submission/claim/summary-fee/CFD
 * graph; it does not mutate ref-data (fee_scheme, area_of_law, matter_type). Downstream stories
 * that DO mutate ref-data must extend this hook with an explicit reset — do not silently pile
 * ref-data clean-up in here as it will slow every non-amendment scenario.
 *
 * <p>Ticket: DSTEW-2301.
 */
@Slf4j
@RequiredArgsConstructor
public class BddAmendmentResetHook {

  private final ClaimAmendmentPersistenceService claimAmendmentPersistenceService;
  private final ValidationService validationService;

  /**
   * Resets both mocks and reapplies default answers. Runs before every cucumber scenario, ahead of
   * {@link BddHooks} (see class-level Javadoc for the ordering rationale).
   */
  @Before(order = -2)
  public void resetAmendmentHarnessMocks() {
    reset(claimAmendmentPersistenceService, validationService);
    clearInvocations(claimAmendmentPersistenceService, validationService);
    applyDefaults();
    log.debug("[DSTEW-2301] Amendment harness mocks reset + defaults applied");
  }

  private void applyDefaults() {
    doCallRealMethod()
        .when(claimAmendmentPersistenceService)
        .persistSuccessfulAmendment(any(), any());

    // validateSubmission stays mocked: it is the submission harness's concern, exercised by many
    // non-amendment scenarios that must not make real outbound validation HTTP. Only validateClaim
    // converges onto the real facade here (DSTEW-2317).
    doReturn(validSubmissionResult()).when(validationService).validateSubmission(any());
    doReturn(validSubmissionResult()).when(validationService).validateSubmission(any(), any());

    // validateClaim converges onto the REAL ValidationService facade for every amendment scenario
    // (DSTEW-2317). validateClaim is only ever called from the amendment external-validation path
    // (AmendmentExternalValidationStep), so calling the real method has zero submission blast
    // radius. Validation behaviour is now armed via MockServer fixtures (PDA /schedules + FSP),
    // not a Mockito doReturn -- closing Ben's PR #455 concern that the happy-path stub bypassed
    // real validation. The @dstew-1753 race scenarios deliberately re-arm a doAnswer barrier on
    // top of this default within their own steps (see AmendmentsFinalSaveGuardSteps); that scoped
    // carve-out is intentional and documented -- HTTP cannot provide the deterministic
    // at-the-validateClaim-boundary seam those concurrency tests need.
    doCallRealMethod().when(validationService).validateClaim(any());
    doCallRealMethod().when(validationService).validateClaim(any(), any());
    doCallRealMethod().when(validationService).validateClaim(any(), any(), any());
  }

  /**
   * A {@link ValidationResult} carrying {@code valid=true} and no issues — the "happy" baseline.
   * The no-arg constructor leaves {@code valid=false} (Java default), which would cause every
   * submission BDD scenario that calls into {@link ValidationService} to 400 with an empty issues
   * list. Explicit {@code setValid(true)} keeps the pre-DSTEW-2301 non-amendment scenarios green.
   */
  private static ValidationResult validSubmissionResult() {
    ValidationResult result = new ValidationResult();
    result.setValid(true);
    return result;
  }
}
