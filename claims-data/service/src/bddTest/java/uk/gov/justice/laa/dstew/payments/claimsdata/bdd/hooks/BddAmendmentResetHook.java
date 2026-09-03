package uk.gov.justice.laa.dstew.payments.claimsdata.bdd.hooks;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.reset;

import io.cucumber.java.Before;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import uk.gov.justice.laa.dstew.payments.claims.validation.core.model.ClaimValidationResult;
import uk.gov.justice.laa.dstew.payments.claims.validation.core.model.ValidationResult;
import uk.gov.justice.laa.dstew.payments.claims.validation.core.service.ValidationService;
import uk.gov.justice.laa.dstew.payments.claimsdata.client.FeeSchemePlatformRestClient;
import uk.gov.justice.laa.fee.scheme.model.FeeCalculationResponse;
import uk.gov.justice.laa.fee.scheme.model.FeeDetailsResponseV2;

/**
 * Cucumber {@code @Before} glue that resets the amendment-harness mocks ({@link
 * FeeSchemePlatformRestClient} and {@link ValidationService}) and reapplies safe defaults before
 * every scenario.
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
 * <p><b>Mock beans</b>: {@link FeeSchemePlatformRestClient} and {@link ValidationService} are
 * declared as {@code @MockitoBean} directly on {@link
 * uk.gov.justice.laa.dstew.payments.claimsdata.bdd.CucumberSpringConfiguration} because Spring's
 * bean-override machinery only picks up mock annotations from the test class that carries
 * {@code @CucumberContextConfiguration}. Defaults cannot be applied via {@code @PostConstruct} on
 * that configuration because the mock beans are wired later; this Cucumber hook is the first
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

  private final FeeSchemePlatformRestClient feeSchemePlatformRestClient;
  private final ValidationService validationService;

  /**
   * Resets both mocks and reapplies default answers. Runs before every cucumber scenario, ahead of
   * {@link BddHooks} (see class-level Javadoc for the ordering rationale).
   */
  @Before(order = -1)
  public void resetAmendmentHarnessMocks() {
    reset(feeSchemePlatformRestClient, validationService);
    applyDefaults();
    log.debug("[DSTEW-2301] Amendment harness mocks reset + defaults applied");
  }

  private void applyDefaults() {
    doReturn(validSubmissionResult()).when(validationService).validateSubmission(any());
    doReturn(validSubmissionResult()).when(validationService).validateSubmission(any(), any());
    doReturn(validClaimResult()).when(validationService).validateClaim(any());
    doReturn(validClaimResult()).when(validationService).validateClaim(any(), any());
    doReturn(validClaimResult()).when(validationService).validateClaim(any(), any(), any());

    doReturn(ResponseEntity.ok(new FeeCalculationResponse()))
        .when(feeSchemePlatformRestClient)
        .calculateFee(any());
    doReturn(ResponseEntity.ok(new FeeDetailsResponseV2()))
        .when(feeSchemePlatformRestClient)
        .getFeeDetails(any());
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

  /**
   * A {@link ClaimValidationResult} carrying {@code valid=true} and no issues — the "happy"
   * baseline for the amendment external-validation step and any other caller.
   */
  private static ClaimValidationResult validClaimResult() {
    ClaimValidationResult result = ClaimValidationResult.builder().build();
    result.setValid(true);
    return result;
  }
}
